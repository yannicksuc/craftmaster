"""CraftMaster "régie vocale": local service turning a spoken French order into show actions.

audio (WAV) + show context  ->  Whisper (faster-whisper)  ->  Claude (tool use)  ->  {transcript, actions, reply}
"""
