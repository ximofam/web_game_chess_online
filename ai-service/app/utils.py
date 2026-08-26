import math


def sigmoid(x: float) -> float:
    """Convert raw cross-encoder logits into normalized probabilities in [0.0, 1.0]."""
    # Clamp x between -50.0 and 50.0 to prevent math.exp overflow
    clamped_x = max(-50.0, min(50.0, float(x)))
    return 1.0 / (1.0 + math.exp(-clamped_x))
