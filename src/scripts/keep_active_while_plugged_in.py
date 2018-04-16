import psutil
import os
import random

# The laptop needs to run unattended, and it is likely at some point in the night power will
# run out. Normally the laptop would keep running until the battery dies, necessitating an
# annoying ritual of getting everything running again the next day. 

# Ubuntu has a setting to mitigate this: "suspend after X idle time while on battery power",
# which, once the generator is cut off, will cause the laptop to do a soft suspend well before
# its battery dies.

# However, as implemented, the idleness counter is active even while AC power is available.
# This means if there is the slightest hiccup in AC power, then if the laptop has not been
# touched in a while (likely), it will suspend immediately.

# This script works around this be fooling Ubuntu into thinking it is active while AC power is
# on (or rather when we still have a certain amount of battery remaining).

#make sure these match the ubuntu power settings!
inactive_time_for_suspend = 30 # minutes
hard_shutdown_at = .02 # battery%

estimated_power_draw = .5 # battery% per hour
safety_margin = 2.

keep_active_battery_threshold = inactive_time_for_suspend * (estimated_power_draw / 60.) * safety_margin + hard_shutdown_at

def keep_active():
    mousemove = random.choice([(1,0), (-1,0), (0,1), (0,-1)])
    os.popen('export DISPLAY=:0 && xdotool mousemove_relative -- %d %d' % mousemove)

if __name__ == "__main__":

    battery_level = psutil.sensors_battery().percent / 100.
    if battery_level > keep_active_battery_threshold:
        keep_active()
