from pathlib import Path
import numpy as np
from resemblyzer import VoiceEncoder, preprocess_wav

VOICEPRINT_PATH = Path("akila_owner_voiceprint.npy")
SIMILARITY_THRESHOLD = 0.78

_encoder = VoiceEncoder()


def enroll_owner_voice(audio_paths: list[str]) -> Path:
    """Create and save the owner's voice embedding from 1-3 short audio clips."""
    embeddings = []
    for path in audio_paths:
        wav = preprocess_wav(Path(path))
        embeddings.append(_encoder.embed_utterance(wav))

    owner_embed = np.mean(embeddings, axis=0)
    owner_embed = owner_embed / np.linalg.norm(owner_embed)
    np.save(VOICEPRINT_PATH, owner_embed)
    return VOICEPRINT_PATH


def is_owner_speaking(incoming_audio_path: str) -> tuple[bool, float]:
    """Verify if incoming speech matches the enrolled owner voiceprint."""
    if not VOICEPRINT_PATH.exists():
        raise FileNotFoundError(
            "Owner voiceprint not found. Run enroll_owner_voice() first."
        )

    owner_embed = np.load(VOICEPRINT_PATH)
    wav = preprocess_wav(Path(incoming_audio_path))
    test_embed = _encoder.embed_utterance(wav)
    test_embed = test_embed / np.linalg.norm(test_embed)

    similarity = float(np.dot(owner_embed, test_embed))
    return similarity >= SIMILARITY_THRESHOLD, similarity
