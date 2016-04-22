float[][] ndata;
float[][] odata;
final float eps = 1;
final float z = 0.2;
PVector light;
PImage image;
boolean rain;

FadecandySketch driver = new FadecandySketch(this, 250, 250);

void setup()
{
  image = loadImage("img.jpg");
  colorMode(HSB, 100);
  ndata = new float[width][height];
  odata = new float[width][height];
  light = new PVector(1, 1, 0);
  light.normalize();
  rain = true;
  driver.init();
  
}

void draw()
{

  if (random(1) < 0.01 && rain)
    ripple(); 

  sim();

  //render the ripples
  loadPixels();

  for (int i = 0; i < pixels.length; i++)
  {
    int x = i % width;
    int y = i / width;
    PVector n = new PVector(getVal(x - eps, y) - getVal(x + eps, y), getVal(x, y - eps) - getVal(x, y + eps), eps * 2);
    n.normalize();
    float spec = (1 - (light.x + n.x)) + (1 - (light.y + n.y));
    spec /= 2;
    if (spec > z)
      spec = (spec - z) / (1 - z);
    else
      spec = 0;


    spec *= 100;

    color c = getC(x + n.x * 60, y + n.y * 60);
    float h = hue(c);
    float s = saturation(c);
    float b = brightness(c) + spec;

    pixels[i] = color(h, s, b);
  }

  updatePixels();
}

void ripple()
{
  int rx = (int)random(width - 10) + 5;
  int ry = (int)random(height - 10) + 5;
  for (int x = -5; x < 5; x++)
    for (int y = -5; y < 5; y++)
      odata[rx + x][ry + y] = 8;
}

color getC(float x, float y)
{
  return image.get((int)x, (int)y);
}

float getVal(float x, float y)
{
  if (x < 1 || y < 1 || x >= width - 1 || y >= height - 1)
    return 0;
  float a = odata[(int)x][(int)y];
  return a;
}

void sim()
{
  float[][] i = odata;
  odata = ndata;
  ndata = i;

  for (int x = 1; x < width - 1; x++)
    for (int y = 1; y < height - 1; y++)
    {
      float val = (odata[x - 1][y] +
        odata[x + 1][y] +
        odata[x][y - 1] +
        odata[x][y + 1]) / 2;
      val -= ndata[x][y];
      val *=  0.96875;
      ndata[x][y] = val;
    }
}

void keyPressed()
{
  rain = !rain;
}

