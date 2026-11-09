"""Transcript + context -> actions, through Claude with one tool per action (regie.schema).

One request, no tool loop: Claude answers with tool_use blocks (several in parallel when the order asks for
several things) that ARE the actions; nothing is sent back to it. The tools and the static system prompt form the
cached prefix (cache_control on the system block); the context and the transcript come after it.
"""
from __future__ import annotations

import logging
import time
from dataclasses import dataclass, field

from .config import Settings, api_key_present
from .context import ShowContext
from .prompt import SYSTEM_PROMPT, user_message
from .schema import TOOL_TO_ACTION, claude_tools
from .validate import check

log = logging.getLogger("regie.claude")

FALLBACK_BETA = "server-side-fallback-2026-07-01"
FAST_BETA = "fast-mode-2026-02-01"


@dataclass
class Understanding:
    actions: list[dict] = field(default_factory=list)
    reply: str = ""
    warnings: list[str] = field(default_factory=list)
    error: str | None = None       # machine-readable: no_api_key, claude_error, refused, empty_transcript
    seconds: float = 0.0
    usage: dict = field(default_factory=dict)


class Understander:
    """client: an anthropic.Anthropic (or a fake with the same beta.messages.create) - created lazily."""

    def __init__(self, settings: Settings, client=None):
        self.settings = settings
        self._client = client
        self.tools = claude_tools()

    def client(self):
        if self._client is None:
            import anthropic  # the key is read by the SDK from the environment, never by us
            self._client = anthropic.Anthropic(timeout=self.settings.timeout_s, max_retries=1)
        return self._client

    def request(self, ctx: ShowContext, transcript: str) -> dict:
        s = self.settings
        params = dict(
            model=s.model,
            max_tokens=4096,
            system=[{"type": "text", "text": SYSTEM_PROMPT, "cache_control": {"type": "ephemeral"}}],
            tools=self.tools,
            tool_choice={"type": "auto"},
            messages=[{"role": "user", "content": user_message(ctx.summary(), transcript)}],
            output_config={"effort": s.effort},
        )
        betas = []
        if s.fallbacks:
            betas.append(FALLBACK_BETA)
            params["fallbacks"] = "default"
        if s.fast_mode:
            betas.append(FAST_BETA)
            params["speed"] = "fast"
        if betas:
            params["betas"] = betas
        return params

    def understand(self, ctx: ShowContext, transcript: str) -> Understanding:
        if not transcript.strip():
            return Understanding(reply="Je n'ai rien entendu.", error="empty_transcript")
        if self._client is None and not api_key_present():
            return Understanding(reply="Pas de clé ANTHROPIC_API_KEY dans l'environnement du service.", error="no_api_key")
        t = time.perf_counter()
        try:
            msg = self.client().beta.messages.create(**self.request(ctx, transcript))
        except Exception as e:  # anthropic.APIError and network errors: say it, never crash the service
            log.warning("Claude call failed: %s: %s", type(e).__name__, e)
            return Understanding(reply=f"Claude injoignable ({type(e).__name__}).", error="claude_error",
                                 seconds=time.perf_counter() - t)
        result = self.parse(msg, ctx)
        result.seconds = time.perf_counter() - t
        return result

    def parse(self, msg, ctx: ShowContext) -> Understanding:
        usage = getattr(msg, "usage", None)
        usage_d = {k: getattr(usage, k, None) for k in ("input_tokens", "output_tokens", "cache_read_input_tokens",
                                                        "cache_creation_input_tokens")} if usage else {}
        if getattr(msg, "stop_reason", None) == "refusal":
            return Understanding(reply="Claude a refusé cette demande.", error="refused", usage=usage_d)
        raw, texts, says = [], [], []
        for block in getattr(msg, "content", []) or []:
            btype = getattr(block, "type", None)
            if btype == "tool_use":
                action = TOOL_TO_ACTION.get(block.name)
                inp = dict(block.input) if isinstance(block.input, dict) else {}
                raw.append({"type": action or block.name, **inp})
            elif btype == "text" and block.text.strip():
                texts.append(block.text.strip())
        checked = check(raw, ctx)
        actions = checked.actions
        says = [a["text"] for a in actions if a["type"] == "say"]
        if says and len(says) < len(actions):
            # "say" next to real actions is only commentary: keep the actions, the text goes to the reply
            actions = [a for a in actions if a["type"] != "say"]
        reply = " ".join(says or texts)
        if not actions and not reply:
            reply = "Je n'ai pas compris : rien n'a été fait."
        if checked.warnings and not actions:
            reply = (reply + " " if reply else "") + "(" + " ; ".join(checked.warnings) + ")"
        return Understanding(actions=actions, reply=reply.strip(), warnings=checked.warnings, usage=usage_d)
