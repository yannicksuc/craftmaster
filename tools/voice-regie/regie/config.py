"""Settings, all from environment variables (start.ps1 sets nothing secret; the API key stays in the environment)."""
import os
from dataclasses import dataclass, field


def _env(name: str, default: str) -> str:
    value = os.environ.get(name, "").strip()
    return value or default


@dataclass
class Settings:
    host: str = "127.0.0.1"
    port: int = 25790
    # Whisper: "auto" picks the GPU when CUDA works, else the CPU (with a smaller model)
    whisper_model: str = "auto"
    whisper_device: str = "auto"
    whisper_compute: str = "auto"
    # "claude" (default) or "offline" (fixed patterns, regie/offline.py)
    understanding: str = "claude"
    # Claude
    model: str = "claude-opus-5-5"
    effort: str = "low"
    fast_mode: bool = False
    fallbacks: bool = True
    timeout_s: float = 12.0
    # Show rules
    idle_minutes: float = 4.0
    log_dir: str = field(default_factory=lambda: os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "logs"))

    @classmethod
    def from_env(cls) -> "Settings":
        return cls(
            host=_env("REGIE_HOST", "127.0.0.1"),
            port=int(_env("REGIE_PORT", "25790")),
            whisper_model=_env("REGIE_WHISPER_MODEL", "auto"),
            whisper_device=_env("REGIE_WHISPER_DEVICE", "auto"),
            whisper_compute=_env("REGIE_WHISPER_COMPUTE", "auto"),
            understanding=_env("REGIE_UNDERSTANDING", "claude"),
            model=_env("REGIE_CLAUDE_MODEL", "claude-opus-5-5"),
            effort=_env("REGIE_CLAUDE_EFFORT", "low"),
            fast_mode=_env("REGIE_CLAUDE_FAST", "0") == "1",
            fallbacks=_env("REGIE_CLAUDE_FALLBACKS", "1") == "1",
            timeout_s=float(_env("REGIE_CLAUDE_TIMEOUT", "12")),
            idle_minutes=float(_env("REGIE_IDLE_MINUTES", "4")),
        )


def api_key_present() -> bool:
    """Only checks that the key exists; it is never read into our own variables, printed or logged."""
    return bool(os.environ.get("ANTHROPIC_API_KEY"))
