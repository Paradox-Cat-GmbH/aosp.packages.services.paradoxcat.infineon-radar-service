Range-Doppler map implementation in LiteRT
==========================================

The script `range_doppler_tflite.py` implements preprocessing of raw data from the Infineon BGT60TR13C
frequency-modulated continuous wave (FMCW) radar into a Range-Doppler map.

We chose to implement this in LiteRT, because AI models for, e.g., classification can then be built on
top of this preprocessing algorithm, and both preprocessing and ML model can be deployed encapsulated in a single
LiteRT model. 

For more information on the theory and algorithms, you can refer to Infineon's Radar SDK documentation at
`radar_sdk/doc/documentation.html`, section "Introduction to Radar".

How to run
----------

Tested with Python 3.12 on Linux.

```bash
python3 -m venv env
. env/bin/activate
pip install tensorflow==2.20.0
python3 range_doppler_tflite.py
```

This produces a LiteRT model called `range_doppler.tflite`, which you can then use on Android.