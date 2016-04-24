import processing.video.*;

final String video_path = "/home/drew/dome/video/the_knife-we_share_our_mothers_health.mp4";
VideoPlayer driver = new VideoPlayer(this, FadecandySketch.widthForPixelDensity(2.5), video_path);

void setup() {
  driver.init();
}

void draw() {
  driver.draw();
}

void keyPressed() {
  driver.keyPressed();
}

void movieEvent(Movie m) { 
  m.read(); 
} 
