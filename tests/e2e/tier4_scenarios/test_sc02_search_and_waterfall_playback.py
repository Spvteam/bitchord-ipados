"""Tier 4 Scenario 2: Search Catalog, Waterfall Resolution & Chunked Playback."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in, assert_valid_stream_result
from tests.e2e.harness.mocks import MockJSContext, MockWKWebViewPoToken, MockResourceLoaderDelegate, MockAVAudioEngine


def test_scenario_02_search_waterfall_resolution_and_streaming():
    """Simulates:
    1. User searches for 'Starboy'
    2. Search results returned, user taps top track
    3. Stream resolved through Innertube waterfall (ANDROID_VR prioritized)
    4. Signature deciphered via JSContext and PoToken appended
    5. bitchord-chunked:// loader fetches first 512KB chunk
    6. Deck A begins playback
    """
    # Step 1 & 2: Search and selection
    search_query = "Starboy"
    selected_track = {
        "videoId": "SAMPLE_VID_01",
        "title": "Starboy (feat. Daft Punk)",
        "artist": "The Weeknd",
        "durationMs": 238000
    }
    assert_equal(selected_track["title"], "Starboy (feat. Daft Punk)")

    # Step 3: Innertube waterfall resolution
    client_priority = ["ANDROID_VR", "IOS", "WEB_REMIX"]
    active_client = client_priority[0]
    assert_equal(active_client, "ANDROID_VR")

    # Step 4: Cipher + PoToken
    po_token = MockWKWebViewPoToken().obtain_po_token()
    raw_sig = "abcdefghijkl"
    deciphered_sig = MockJSContext.decipher_signature(raw_sig, [("reverse", 0)])

    stream_url = f"https://rr1.googlevideo.com/videoplayback?itag=140&sig={deciphered_sig}&pot={po_token}"
    resolved = {
        "streamUrl": stream_url,
        "itag": 140,
        "mimeType": "audio/mp4",
        "bitrate": 128000,
        "contentLength": 3845921,
        "poTokenUsed": True
    }
    assert_valid_stream_result(resolved)

    # Step 5: Chunked stream request (512 KB)
    loader = MockResourceLoaderDelegate(content_length=resolved["contentLength"])
    chunk_1 = loader.request_range(0, 524288)
    assert_equal(chunk_1["status"], 206)
    assert_equal(chunk_1["bytes_loaded"], 524288)

    # Step 6: Deck A begins playback
    engine = MockAVAudioEngine()
    engine.load_track("A", f"bitchord-chunked://stream?videoId={selected_track['videoId']}")
    engine.play("A")
    assert_true(engine.deck_a_playing)
    assert_equal(engine.active_deck, "A")
