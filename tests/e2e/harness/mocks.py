"""Simulators and contracts for Apple-native and KMP actuals for BitChord E2E testing."""

import hashlib
import json
import math
import time
import urllib.parse
from typing import Dict, Any, Optional, List, Tuple


class MockPlatformCrypto:
    """Simulates Apple CoreCrypto CC_SHA1 and CC_MD5."""

    @staticmethod
    def sha1_string(data: str) -> str:
        return hashlib.sha1(data.encode("utf-8")).hexdigest().lower()

    @staticmethod
    def sha1_bytes(data: bytes) -> str:
        return hashlib.sha1(data).hexdigest().lower()

    @staticmethod
    def md5_string(data: str) -> str:
        return hashlib.md5(data.encode("utf-8")).hexdigest().lower()

    @staticmethod
    def md5_bytes(data: bytes) -> str:
        return hashlib.md5(data).hexdigest().lower()

    @staticmethod
    def generate_sapisid_hash(timestamp_ms: int, sapisid: str, origin: str = "https://music.youtube.com") -> str:
        """Generates Google SAPISIDHASH: ${timestamp}_${sha1("$timestamp $sapisid $origin")}"""
        payload = f"{timestamp_ms} {sapisid} {origin}"
        digest = MockPlatformCrypto.sha1_string(payload)
        return f"{timestamp_ms}_{digest}"


class MockPlatformClock:
    """Simulates Apple platform clock."""
    def __init__(self, initial_ms: Optional[int] = None):
        self._current_ms = initial_ms if initial_ms is not None else int(time.time() * 1000)

    def current_time_millis(self) -> int:
        return self._current_ms

    def advance_millis(self, ms: int):
        self._current_ms += ms


class MockPlatformUrl:
    """Simulates PlatformUrl encoding/decoding."""
    @staticmethod
    def encode(value: str) -> str:
        return urllib.parse.quote(value, safe="")

    @staticmethod
    def decode(value: str) -> str:
        return urllib.parse.unquote(value)


class MockJSContext:
    """Simulates JavaScriptCore JSContext for YouTube signature and n-param transformations."""

    @staticmethod
    def decipher_signature(sig: str, operations: List[Tuple[str, int]]) -> str:
        """Applies sequence of signature transformations (reverse, slice, swap)."""
        if not sig:
            return ""
        chars = list(sig)
        for op, arg in operations:
            if not chars:
                break
            if op == "reverse":
                chars.reverse()
            elif op == "splice":
                chars = chars[arg:]
            elif op == "swap":
                idx = arg % len(chars)
                chars[0], chars[idx] = chars[idx], chars[0]
        return "".join(chars)

    @staticmethod
    def transform_n_param(n_token: str) -> str:
        """Simulates YouTube n-parameter deobfuscation transform."""
        if not n_token:
            return ""
        # Deterministic transform matching JS engine output
        chars = list(n_token)
        chars.reverse()
        return "".join(chars) + "_trans"


class MockWKWebViewPoToken:
    """Simulates 3-stage BotGuard PoToken generator with 6-hour caching."""

    CACHE_TTL_MS = 6 * 60 * 60 * 1000  # 6 hours

    def __init__(self, clock: Optional[MockPlatformClock] = None):
        self.clock = clock or MockPlatformClock()
        self._cached_token: Optional[str] = None
        self._cached_at_ms: int = 0
        self.handshake_stage: str = "IDLE"

    def obtain_po_token(self, force_refresh: bool = False) -> str:
        now = self.clock.current_time_millis()
        if (
            not force_refresh
            and self._cached_token
            and (now - self._cached_at_ms) < self.CACHE_TTL_MS
        ):
            return self._cached_token

        # Execute 3-stage BotGuard handshake
        # Stage 1: Create request
        self.handshake_stage = "CREATE_REQUEST"
        challenge_id = "botguard_challenge_" + MockPlatformCrypto.sha1_string(str(now))[:12]

        # Stage 2: VM Execution & GenerateIT
        self.handshake_stage = "GENERATE_IT"
        integrity_token = "IT_" + MockPlatformCrypto.sha1_string(challenge_id)[:16]

        # Stage 3: Mint PoToken
        self.handshake_stage = "MINT_TOKEN"
        token = "PO_" + MockPlatformCrypto.sha1_string(f"{integrity_token}_{now}")[:32]

        self._cached_token = token
        self._cached_at_ms = now
        self.handshake_stage = "COMPLETE"
        return token

    def is_cached(self) -> bool:
        now = self.clock.current_time_millis()
        return bool(self._cached_token and (now - self._cached_at_ms) < self.CACHE_TTL_MS)


class MockIOSKeychain:
    """Simulates iOS hardware-backed Keychain service (SecItem API)."""

    def __init__(self, service_name: str = "com.music.bitchord.auth"):
        self.service_name = service_name
        self._store: Dict[str, Dict[str, Any]] = {}

    def sec_item_add(self, account: str, secret: str, accessibility: str = "kSecAttrAccessibleAfterFirstUnlock") -> int:
        if account in self._store:
            return -25299  # errSecDuplicateItem
        self._store[account] = {
            "value": secret,
            "accessibility": accessibility,
            "service": self.service_name
        }
        return 0  # errSecSuccess

    def sec_item_copy_matching(self, account: str) -> Optional[str]:
        if account not in self._store:
            return None
        return self._store[account]["value"]

    def sec_item_update(self, account: str, new_secret: str) -> int:
        if account not in self._store:
            return -25300  # errSecItemNotFound
        self._store[account]["value"] = new_secret
        return 0

    def sec_item_delete(self, account: str) -> int:
        if account not in self._store:
            return -25300  # errSecItemNotFound
        del self._store[account]
        return 0


class MockAVAudioEngine:
    """Simulates iPadOS Dual-Deck AVAudioEngine with Equal-Power Crossfade and 10-Band EQ."""

    def __init__(self):
        self.deck_a_playing = False
        self.deck_b_playing = False
        self.deck_a_volume = 1.0
        self.deck_b_volume = 0.0
        self.deck_a_rate = 1.0
        self.deck_b_rate = 1.0
        self.deck_a_pitch_cents = 0.0
        self.deck_b_pitch_cents = 0.0
        self.eq_bands = [0.0] * 10
        self.is_crossfading = False
        self.active_deck = "A"

    def load_track(self, deck: str, url: str):
        if deck == "A":
            self.deck_a_volume = 1.0
        else:
            self.deck_b_volume = 0.0

    def play(self, deck: str):
        if deck == "A":
            self.deck_a_playing = True
        elif deck == "B":
            self.deck_b_playing = True

    def pause(self, deck: str):
        if deck == "A":
            self.deck_a_playing = False
        elif deck == "B":
            self.deck_b_playing = False

    def compute_equal_power_crossfade(self, progress: float) -> Tuple[float, float]:
        """
        Equal-power crossfade law:
        Gain_A = cos(pi / 2 * t)
        Gain_B = sin(pi / 2 * t)
        Satisfies Gain_A^2 + Gain_B^2 = 1.0 (constant acoustic power, 0 dB dip).
        """
        if progress <= 0.0:
            return 1.0, 0.0
        if progress >= 1.0:
            return 0.0, 1.0
        t = progress
        angle = (math.pi / 2.0) * t
        gain_a = math.cos(angle)
        gain_b = math.sin(angle)
        return gain_a, gain_b

    def apply_crossfade_step(self, progress: float):
        self.is_crossfading = True
        gain_a, gain_b = self.compute_equal_power_crossfade(progress)
        self.deck_a_volume = gain_a
        self.deck_b_volume = gain_b
        if progress >= 1.0:
            self.is_crossfading = False
            self.deck_a_playing = False
            self.active_deck = "B"

    def set_equalizer_bands(self, gains: List[float]):
        if len(gains) != 10:
            raise ValueError(f"10 bands required, got {len(gains)}")
        for g in gains:
            if not (-12.0 <= g <= 12.0):
                raise ValueError(f"EQ gain out of range [-12, +12] dB: {g}")
        self.eq_bands = list(gains)

    def set_deck_pitch_and_rate(self, deck: str, rate: float, pitch_cents: float):
        if not (0.5 <= rate <= 2.0):
            raise ValueError(f"Rate out of range [0.5, 2.0]: {rate}")
        if not (-2400.0 <= pitch_cents <= 2400.0):
            raise ValueError(f"Pitch out of range [-2400, +2400]: {pitch_cents}")
        if deck == "A":
            self.deck_a_rate = rate
            self.deck_a_pitch_cents = pitch_cents
        else:
            self.deck_b_rate = rate
            self.deck_b_pitch_cents = pitch_cents


class MockResourceLoaderDelegate:
    """Simulates AVAssetResourceLoaderDelegate intercepting bitchord-chunked:// streams."""

    CHUNK_SIZE = 524288  # 512 KB

    def __init__(self, content_length: int = 4000000):
        self.content_length = content_length
        self.loaded_bytes = 0
        self.refusal_count = 0
        self.recovery_successful = False

    def request_range(self, start_offset: int, length: int, simulate_403: bool = False) -> Dict[str, Any]:
        if simulate_403:
            self.refusal_count += 1
            return {"status": 403, "data": None, "headers": {}}

        end_offset = min(start_offset + length - 1, self.content_length - 1)
        actual_bytes = max(0, end_offset - start_offset + 1)
        self.loaded_bytes += actual_bytes
        return {
            "status": 206,
            "range": f"bytes {start_offset}-{end_offset}/{self.content_length}",
            "bytes_loaded": actual_bytes,
            "data": b"\x00" * actual_bytes
        }

    def handle_403_recovery(self, new_token: str, resume_offset: int) -> bool:
        """Simulates 403 recovery with refreshed BotGuard PoToken."""
        if new_token and new_token.startswith("PO_"):
            self.recovery_successful = True
            return True
        return False
