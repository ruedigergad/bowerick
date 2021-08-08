
# Particles Example in Python

## Setup

```
virtualenv -p python3.8 env
source env/bin/activate
pip3 install -r requirements.txt
```

## Run-time Preparation

Run a broker with a corresponding message generator, e.g., as follows:

```
java -jar dist/bowerick-2.8.0-standalone.jar -G yin-yang -I 40 -u '[tcp://127.0.0.1:1031 mqtt://127.0.0.1:1701 ws://127.0.0.1:1864 stomp://127.0.0.1:2000]'
```

## Run

```
# If not done already, enter the virtual environment.
source env/bin/activate
./python_particles_example.py
```

