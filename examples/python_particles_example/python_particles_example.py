#!/usr/bin/env python3

# Copyright 2021, Ruediger Gad
# License: MIT License

import json
import matplotlib.pyplot as plt
import numpy as np
import queue
import stomp

running = True
def on_gui_close(event):
    global running
    running = False

plt.ion()

fig = plt.figure()
fig.canvas.mpl_connect('close_event', on_gui_close)

ax = fig.add_subplot(projection='3d')
p3d = ax.scatter([], [], [])
ax.set_xlim3d([-1.2, 1.2])
ax.set_ylim3d([-1.2, 1.2])
ax.set_zlim3d([-1.2, 1.2])

data_queue = queue.Queue()

class ParticleListener(stomp.ConnectionListener):
    def on_before_message(self, frame):
        pass

    def on_message(self, frame):
        global data_queue

        particles = json.loads(frame)

        x = list(map(lambda d: d['x'], particles))
        y = list(map(lambda d: d['y'], particles))
        z = list(map(lambda d: d['z'], particles))
       
        colors = list(map(lambda d: [d['color_r'], d['color_g'], d['color_b']], particles))
       
        data = {}
        data['coordinates'] = np.array((x, y, z), dtype=float)
        data['sizes'] = np.array(list(map(lambda d: d['scale_y'] * 500.0, particles)), dtype=float)
        data['colors'] = np.array(colors, dtype=float)
        data_queue.put(data)

conn = stomp.Connection([('127.0.0.1', 2000)])
conn.connect(wait=True)
conn.subscribe('/topic/bowerick.message.generator', 1)
conn.set_listener('particles', ParticleListener)

print('Started.')
while running:
    data = data_queue.get()

    p3d._offsets3d = data['coordinates']
    p3d.set_sizes(data['sizes'])
    p3d.set_edgecolors(data['colors'])
    p3d.set_facecolors(data['colors'])
    
    plt.draw()
    plt.pause(0.01)

print('Shutting down...')
conn.remove_listener('particles')
conn.disconnect()
