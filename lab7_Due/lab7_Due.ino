#include <adk.h>
#include "variant.h"
#include <stdio.h>

//definizione pin collegati al LED, sensori Ping e luce
#define LED_PIN  13
int IR_PIN  = A0;

// Accessory descriptor. It's how Arduino identifies itself to Android.
char descriptionName[] = "UDOOAndroidADKDemoBidirect"; // the app installed in Android
char modelName[] = "UDOO_ADK_BIDIRECT"; // your Arduino Accessory name (Need to be the same defined in the Android App)
char manufacturerName[] = "Aidilab";  // manufacturer (Need to be the same defined in the Android App)

// Make up anything you want for these
char versionNumber[] = "1.0"; // version (Need to be the same defined in the Android App)
char serialNumber[] = "1";
char url[] = "http://www.aidilab.it"; // If there isn't any compatible app installed, Android suggest to visit this url
                                     
USBHost Usb;
ADK adk(&Usb, manufacturerName, modelName, descriptionName, versionNumber, url, serialNumber);

#define RCVSIZE 3   
uint8_t buf[RCVSIZE];
uint32_t bytesRead = 0;

uint8_t bufWrite[3];    

int pulse = 0;                 
int prevPulse = 0;
int rate = 0;
int prevRate = 0;
int loc = 0;
int prevLoc = 0;

void setup()
{
    Serial.begin(9600);
    Serial1.begin(9600);
    pinMode(LED_PIN, OUTPUT);
    delay(500);
}

void loop()
{   

    Usb.Task();
   // int incoming = Serial.available();  
   
    if (adk.isReady()) {  // check if device ready for communication
        adk.read(&bytesRead, RCVSIZE, buf); // read data into bufRead array
        if (bytesRead > 0) {          
            if (parseCommand(buf[0]) == 1) {// compare received data
                // Received "1" - turn on LED
                digitalWrite(LED_PIN, HIGH);
                Serial1.print('H');
            } else if (parseCommand(buf[0]) == 0) {
                // Received "0" - turn off LED
                digitalWrite(LED_PIN, LOW);
                Serial1.print('L');
            }
        }
        String myString = Serial1.readStringUntil('\n');   // read data receive from UNO
        int commaIndex = myString.indexOf(',');  // get the index of the first comma
        int secondCommaIndex = myString.indexOf(',', commaIndex+1); // get the index of the second comma
        bufWrite[0] = (uint8_t)(myString.substring(0, commaIndex).toInt());  // get the value from index 0 to index of the first comma
        bufWrite[1] = (uint8_t)(myString.substring(commaIndex+1, secondCommaIndex).toInt()); // get the value after first comma index to second comma index
        bufWrite[2] = (uint8_t)(myString.substring(secondCommaIndex+1).toInt()); // get the value after second comma index to the end of the string     
        // sensor data
        pulse = bufWrite[0];
        rate = bufWrite[1];
        loc = bufWrite[2];
        // read the data in value:
        Serial.print(pulse);
        Serial.print(" ");
        Serial.print(rate);
        Serial.print(" ");
        Serial.print(loc);
        Serial.println();
        adk.write(sizeof(bufWrite), (uint8_t *)bufWrite); //write the values to Android
    } else {
      digitalWrite(LED_PIN, LOW);
    } 
    delay(100);
}

// the characters sent to Arduino are interpreted as ASCII, we decrease 48 to return to ASCII range.
uint8_t parseCommand(uint8_t received) {
  return received - 48;
}
