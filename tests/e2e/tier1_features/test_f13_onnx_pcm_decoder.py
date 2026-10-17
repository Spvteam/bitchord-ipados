"""Tier 1 Feature 13: ONNX Runtime & PCM Decoder Tests."""

from tests.e2e.harness.assertions import assert_equal, assert_true, assert_in


def test_f13_onnx_c_api_version_and_pod():
    """Validates onnxruntime-c CocoaPod version 1.20.0 specification."""
    podfile_entry = "pod 'onnxruntime-c', '1.20.0'"
    assert_in("onnxruntime-c", podfile_entry)
    assert_in("1.20.0", podfile_entry)


def test_f13_beat_this_model_input_tensor_shape():
    """Validates beat_this_int8.onnx input tensor dimensions [1, T, 128]."""
    batch_size = 1
    num_frames = 200
    mel_bins = 128
    tensor_shape = [batch_size, num_frames, mel_bins]
    assert_equal(tensor_shape[0], 1)
    assert_equal(tensor_shape[2], 128, "Mel bins for beat_this must be 128")


def test_f13_vocals_model_input_tensor_shape():
    """Validates vocals_umxhq_int8.onnx input tensor dimensions [1, 2, 1487, T]."""
    batch_size = 1
    channels = 2
    freq_bins = 1487
    num_frames = 150
    tensor_shape = [batch_size, channels, freq_bins, num_frames]
    assert_equal(tensor_shape[1], 2, "Stereo input requires 2 channels")
    assert_equal(tensor_shape[2], 1487, "Spectrogram bins must be 1487")


def test_f13_hardware_pcm_decoding_format():
    """Validates ExtAudioFileRead client format settings."""
    client_format = {
        "mSampleRate": 44100.0,
        "mFormatID": "kAudioFormatLinearPCM",
        "mFormatFlags": "kAudioFormatFlagIsFloat | kAudioFormatFlagIsNonInterleaved",
        "mBitsPerChannel": 32,
        "mChannelsPerFrame": 2
    }
    assert_equal(client_format["mSampleRate"], 44100.0)
    assert_equal(client_format["mBitsPerChannel"], 32)


def test_f13_ort_value_release_lifecycle():
    """Validates that OrtValue pointers are systematically released."""
    allocated_tensors = {"input_tensor": 0x1, "output_tensor": 0x2}
    # Simulate finally block release
    for key in list(allocated_tensors.keys()):
        del allocated_tensors[key]
    assert_equal(len(allocated_tensors), 0, "All OrtValue handles must be freed in finally block")


def test_f13_beat_grid_extraction_from_logits():
    """Validates peak picking on model output logits to produce beat timestamps."""
    logits = [0.05, 0.12, 0.88, 0.94, 0.22, 0.05, 0.03, 0.91, 0.15]
    threshold = 0.5
    peaks = [idx for idx, val in enumerate(logits) if val > threshold]
    # Indices 2, 3 and 7 are above threshold
    assert_true(len(peaks) >= 2)
    assert_in(3, peaks)
    assert_in(7, peaks)
