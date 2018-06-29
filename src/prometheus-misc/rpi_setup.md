# Prometheus

## Pi Setup

1. Start with Raspbian Stretch Lite image
2. Enable SSH (`raspi-config`)
3. Set hostname to `wing-n`

Then:

```
sudo apt-get -y install netatalk
sudo apt-get -y install git
sudo apt-get -y install emacs
git clone git://github.com/scanlime/fadecandy
cd fadecandy/server
make submodules
make
sudo mv fcserver /usr/local/bin
```

Place `fcserver.json` in `/home/pi`, Then, add

```
/usr/local/bin/fcserver /home/pi/fcserver.json >/var/log/fcserver.log 2>&1 &
```

to `/etc/rc.local`

Static IP config: in `/etc/dhcpcd.conf`:

```
interface eth0
static ip_address=192.168.1.11/24
static routers=192.168.1.2
static domain_name_servers=8.8.8.8
```

where the IP on line 2 is as different for each wing.
