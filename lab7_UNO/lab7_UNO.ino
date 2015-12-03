#include <PinChangeInt.h>
#include <PinChangeIntConfig.h>

#include <eHealth.h>
#include <eHealthDisplay.h>

#define BUFFERSIZE 5

int cont = 0;
int led = 13;
char buffer;

int bpmBuffer[BUFFERSIZE];
int oxySatBuffer[BUFFERSIZE];

void setup() {
  Serial.begin(9600);
  eHealth.initPulsioximeter();
  eHealth.initPositionSensor();
  //Attach the interruptions for using the pulsioximeter.   
  PCintPort::attachInterrupt(6, readPulsioximeter, RISING);
  pinMode(led, OUTPUT);

  // initialise reading buffers
  for (int i = 0; i != BUFFERSIZE; ++i) {
    bpmBuffer[i] = 0;
    oxySatBuffer[i] = 0;
  }
}

void loop() { 
  updateBuffers(); // get reading from sensor and add to buffer
  int bpm = getAverageBPM();  // get average from buffer
  int oxySat = getAverageOxySat(); // get average from buffer
  //int bpm = eHealth.getBPM();  // get heart bits per minutes
  //int oxySat = eHealth.getOxygenSaturation(); // get oxygen saturation in blood
  int position= eHealth.getBodyPosition();  // get body position
  Serial.print((uint8_t)bpm);  // print heart bits per minute in serial monitor and send value to XBee module
  Serial.print(',');
  Serial.print((uint8_t)oxySat); 
  Serial.print(',');
  Serial.print((uint8_t)position);  // print body position in serial monitor and send value to XBee module
  Serial.println();
  delay(490);
  while (Serial.available()) {     // check if there is data in serial port
    delay(10);  // delay for 10 milliseconds
    buffer = Serial.read();   // read input data into buffer
    if (buffer == 'H') {
       digitalWrite(led, HIGH);   // light up the LED
      } else if (buffer == 'L') {
       digitalWrite(led, LOW);  // turn off the LED
      }
  }
  
}


//Include always this code when using the pulsioximeter sensor
//=========================================================================
void readPulsioximeter(){  

  cont ++;

  if (cont == 50) { //Get only of one 50 measures to reduce the latency
    eHealth.readPulsioximeter();  // read current values of sensors
    cont = 0;
  }
}

void updateBuffers() {
    static int index = 0;
    bpmBuffer[index] = eHealth.getBPM(); // get heart bits per minutes
    oxySatBuffer[index] = eHealth.getOxygenSaturation(); // get oxygen saturation in blood
    index = (index + 1) % BUFFERSIZE;
}

int getAverageBPM() {
    int bpmSum = 0;
    for (int i = 0; i != BUFFERSIZE; ++i)
        bpmSum += bpmBuffer[i];
    return bpmSum / BUFFERSIZE;
}

int getAverageOxySat() {
    int oxySatSum = 0;
    for (int i = 0; i != BUFFERSIZE; ++i)
        oxySatSum += oxySatBuffer[i];
    return oxySatSum / BUFFERSIZE;
}
