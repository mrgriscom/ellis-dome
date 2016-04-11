# Notes on deploying this stuff

There's two components to this:

1. Deploying `fcserver` itself, to the device with the Fadecandies attached. This will create the OPC server that drives the LEDs.
2. Deploying the animation software, which will send video frames to the candy server.

Note that `fcserver` is unaware of audio, the animation software should look after its own audio requirements. 

## Setting up `fcserver` on a Raspberry Pi

Testing was done with a Pi 2. No idea how this works on other versions. Mainly followed this [tutorial](https://learn.adafruit.com/1500-neopixel-led-curtain-with-raspberry-pi-fadecandy/fadecandy-server-setup) from Adafruit, but in essence:

SSH in, and in homefolder:

    sudo apt-get -y install git
    git clone git://github.com/scanlime/fadecandy
    cd fadecandy/server
    make submodules
    make
    
    sudo mv fcserver /usr/local/bin
    
Then, to auto start

    sudo nano /etc/rc.local
    
Just above the final “exit 0” line, copy and paste the following:

    /usr/local/bin/fcserver /usr/local/bin/fcserver.json &gt;/var/log/fcserver.log 2&gt;&amp;1 &amp;
    
Then, make the config file:

    sudo nano /usr/local/bin/fcserver.json
    
Use the files in `/src/config` as templates, depending on the actual panel configuration.

## Alternative: running `fcserver` on TL-MR3040

The TP-Link MR3040 is a neat little device. It's a battery powered WiFi AP/Router, with one USB 2.0 port, one BASE100 ethernet port, and a 1x1 radio. Compared to a Pi, this has:

* Built in LiPo battery/charger;
* WiFi AP (only 2.4Ghz 802.11n, but doubt frequency saturation is a problem in the desert);
* A bit cheaper at time of writing (~$30 v.s. $40);
* Comes in a case;
* Only one USB 2.0 port, so we need a hub.

In place of Raspbian for the Pis, we can use OpenWRT on it. Some one ported `fcserver` to run on it [here](https://github.com/nemik/fadecandy-openwrt) with [instructions here](http://blog.nemik.net/2014/02/standalone-openwrt-fadecandy-server-for-led-control/).

Performance seems OK with the 1560 LED setup, across 4 Fadecandies.

Following the gide, we should be able to connect via the `Fadecandy` SSID. However, the GUI is disabled by default, so SSH into `192.168.2.1` with `root:root` to config. Use `scp` to copy `/src/config/13panel-fc.json` to `/etc/fcserver.json` (check this part). Reboot the MR3040 and be done.

Note on using the MR3040 in its default configuration: you can configure it to do pretty much what you like, but you need to know how it works a bit. By default, it sits on two subnets (is that right?). One is connected to the WiFi radio, where it acts as DHCP server. Connect to this "in the field" where there's no network infrastructure. The ethernet port, on the other hand, accepts DHCP leases, so it will join your existing network. It might take a bit of effort to find its IP, but use this "in the lab", since you can connect to both it and the internet. 

We are using an [Amazon Basic 4-port USB 3.0 hub](http://www.amazon.com/AmazonBasics-Port-2-5A-power-adapter/dp/B00DQFGH80/) to connect the 4 Fadecandies, no external power required.

## Steps taken to run sketches on Raspbian

You have to be in X to run processing sketches. So to autoboot into it, there things were done, in no particular order.

### Exporting the sketch

Export the sketch, targeting Linux (don't need to worry about arch)

### Setup the Pi so that the sketch runs 

We are working with Processing 2.x (most likely 2.2), apparently, Java 7 works best. Install it by


    sudo apt-get install oracle-java7-jdk

and ensure it's the default Java by running:


    sudo update-alternatives --config java


Now try to run the sketch (once you are in X) by typing `sh /path/yoursketch`. If you get something about can't use pixels on this device, it might be [this issue](https://github.com/processing/processing/issues/2010.html).

The fix is to force 32 bit mode. Adding this to the bottom of `\boot\config.txt` seems to work:


    framebuffer_depth=32
    framebuffer_ignore_alpha=1


### Automatically launch into X, then the sketch

`sudo raspi-config` launches a config utility. One of the options there allows you to launch X automatically on boot.

Add a .desktop file to the startup folder (`/etc/xdg/autostart`)

format is something like this:


    [Desktop Entry]
    Name=Cloud Autostart
    Exec=sh /path/yoursketch
    Type=Application
    Terminal=true


This also seem to run without screensaver/power saving issues. Not sure if its anything in the config, or just what a Pi with Raspbian does.

#### Possible alternative:

Tried adding `@/bin/sh /path/yoursketch` to end of `/etc/xdg/lxsession/LXDE/autostart` but it didn't work.

Maybe it should be added to `LXDE-pi/autostart`? 