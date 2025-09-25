import keras
import tensorflow as tf


def normalize(x):
    """
    Normalizes a window function by dividing by its sum
    """
    return x / tf.math.reduce_sum(x)


def rfft(x):
    """
    FFT of a real-valued signal along its last axis.
    Real and imaginary part will be stacked on axis 0.

    The RFFT operation only returns the positive half of the FFT result, the negative half is its complex conjugate:
    FFT(-x) = FFT(x)*
    """
    result = tf.signal.rfft(x)
    return tf.stack([tf.math.real(result), tf.math.imag(result)], axis=0)


def rfft_full(x):
    """
    FFT of a real-valued signal along its last axis, including positive and negative frequencies.
    """
    x_rfft = rfft(x)
    neg_part = tf.transpose(tf.transpose(x_rfft[..., -2:0:-1]) * tf.constant([1.0, -1.0]))
    return tf.concat([x_rfft, neg_part], -1)


def fft(x):
    """
    FFT of a complex-valued signal along its last axis.
    The complex-valued signal should be passed as a float tensor with real and imaginary part stacked along axis 0.

    TFLite does not implement FFT, so we reproduce it using RFFT:
    FFT(x) = Re(FFT(Re(x)) - Im(FFT(Im(x)) + i (Im(FFT(Re(x)) + Re(FFT(Im(x)))
    ref.: https://www.dsprelated.com/showarticle/97.php
    """
    assert x.shape[0] == 2
    x_rfft = rfft_full(x)
    return tf.stack([x_rfft[0, 0] - x_rfft[1, 1], x_rfft[1, 0] + x_rfft[0, 1]], axis=0)


def fftshift(x):
    """
    In the result of fft, shifts frequency bins so that frequencies are in ascending order, with the zero-frequency
    component at the center of the spectrum.
    """
    idx = x.shape[-1] // 2
    return tf.concat([x[..., idx:], x[..., :idx]], axis=-1)


def complex_abs(x):
    """
    Absolute value of a complex signal.
    The complex data should be passed as a float tensor with real and imaginary part stacked along axis 0.
    """
    return tf.math.sqrt(tf.math.reduce_sum(x ** 2, axis=0))


class RangeDopplerMap(keras.Model):
    """
    Keras model that produces a Range-Doppler map from raw FMCW radar data.
    """
    def __init__(self):
        super().__init__()
        self.n_chirps = 32
        self.n_samples_per_chirp = 64

        self.range_window = normalize(tf.signal.hann_window(self.n_samples_per_chirp, periodic=False))
        self.doppler_window = normalize(tf.signal.kaiser_window(self.n_chirps, beta=25))

    def range_fft(self, x):
        # subtract the mean from each chirp
        x = x - tf.math.reduce_mean(x, axis=-1, keepdims=True)
        # apply window
        x *= self.range_window
        # FFT along fast time
        x_fft = rfft(x)
        return x_fft[..., :-1]

    def doppler_fft(self, x):
        # bring axes into the right order, we want to FFT along the second-to-last axis (slow time)
        x = tf.transpose(x, [0, 1, 3, 2]) * self.doppler_window

        # calculate FFT
        x_fft = fft(x)
        x_fft = fftshift(x_fft)

        # transpose back
        x_fft = tf.transpose(x_fft, [0, 1, 3, 2])
        return x_fft

    def call(self, frame):
        # input shape: (n_antenna, n_chirps, n_samples_per_chirp)

        # Range FFT (fast-time)
        range_fft = self.range_fft(frame)
        # Mean removal (moving target indicator)
        range_fft_mti = range_fft - tf.math.reduce_mean(range_fft, axis=-2, keepdims=True)
        # Doppler FFT (slow-time)
        range_doppler = self.doppler_fft(range_fft_mti)
        # Absolute value
        range_doppler_abs = complex_abs(range_doppler)

        # convert to uint8 image for display purposes
        image = tf.reverse(tf.transpose(range_doppler_abs[0] * 1e3), axis=[0])
        return tf.cast(tf.clip_by_value(image, 0, 1) * 255, tf.uint8)


if __name__ == '__main__':
    model = RangeDopplerMap()
    testdata = tf.random.uniform((3, 32, 64))
    model(testdata)
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    tflite_model = converter.convert()

    # Save the model.
    model_path = 'range_doppler.tflite'
    with open(model_path, 'wb') as f:
        f.write(tflite_model)

    tf.lite.experimental.Analyzer.analyze(model_path)