#include <WiFi.h>
#include <WiFiUdp.h>

#define NUM 2

#define BUZZER_PORT 5

int Modbus_RTU_Address = 2;
byte blink_flag = 0;
hw_timer_t *timer0 = NULL;
volatile SemaphoreHandle_t timerSemaphore;
portMUX_TYPE timerMux = portMUX_INITIALIZER_UNLOCKED;

const char *ssid = "Wi-Fi";
const char *password = "hq1105364324";

char clear_consumption[8] = { 0x00, 0x06, 0x0C, 0x26, 0x12, 0x34, 0xFF, 0xFF };
char main_request[8] = { 0x00, 0x03, 0x0B, 0xB8, 0x00, 0x06, 0xFF, 0xFF };
char full_request[8] = { 0x00, 0x03, 0x0B, 0xB8, 0x00, 0x0C, 0xFF, 0xFF };

int alarm_set[NUM] = { 0 };
unsigned long limit = 200000;

int alarm_flag[NUM] = { 0 };
int state_flag[NUM] = { 0 };
int count_down[NUM] = { 0 };

int ports[NUM] = {18, 19};

unsigned long voltage[NUM] = { 0 };
unsigned long current[NUM] = { 0 };
unsigned long power_prev[NUM] = { 0 };
unsigned long power_next[NUM] = { 0 };
long factor[NUM] = { 0 };
unsigned long frequency[NUM] = { 0 };
unsigned long consumption[NUM] = { 0 };

char recieve[40] = { 0 };
char incomingPacket[40];

HardwareSerial Serial2(2);

WiFiUDP UDP;

unsigned int ModBusCRC(char *ptr, unsigned char len)
{
  unsigned int CRC16;
  CRC16 = 0xFFFF;
  for (int i = 0; i < len; i++)
  {
    CRC16 = *ptr ^ CRC16;
    for (int j = 0; j < 8; j++)
    {
      int temp = CRC16 & 0x0001;
      CRC16 = CRC16 >> 1;
      if (temp)
        CRC16 = CRC16 ^ 0xA001;
    }
    ptr++;
  }
  return ((CRC16 & 0x00FF) << 8) | ((CRC16 & 0xFF00) >> 8);
}

void IRAM_ATTR onTimer()
{
  if (Modbus_RTU_Address + 1 > NUM)
    Modbus_RTU_Address = 1;
  else
    Modbus_RTU_Address++;

  full_request[0] = Modbus_RTU_Address;
  unsigned int CRC16 = ModBusCRC(full_request, 6);
  full_request[6] = (CRC16 & 0xFF00) >> 8;
  full_request[7] = (CRC16 & 0x00FF) >> 0;

  Serial.printf("Modbus RTU Send: ");
  for (int i = 0; i < 7; i++) {
    Serial.printf("%02X ", full_request[i]);
  }
  Serial.printf("%02X\n", full_request[7]);
  Serial2.write((uint8_t *)full_request, 8);

  int index = 0;
  while (Serial2.available() > 0)
    recieve[index++] = Serial2.read();

  Serial.printf("Modbus RTU Receive: ");
  for (int i = 0; i < index - 1; i++) {
    Serial.printf("%02X ", recieve[i]);
  }
  Serial.printf("%02X\n", recieve[index - 1]);

  CRC16 = ModBusCRC(recieve, index - 2);
  if (CRC16 == (recieve[index - 2] << 8) | recieve[index - 1]) {
    Serial.printf("CRC16 Check Correct!\n");
    if (recieve[1] == 0x03) {
      Serial.printf("Modbus Read Correct!\n");
      if (recieve[2] == 0x18) {
        Serial.printf("Modbus Length Correct!\n");
        portENTER_CRITICAL_ISR(&timerMux);
        voltage[recieve[0] - 1] = 0;

        for (int j = 3; j < 7; j++)
          voltage[recieve[0] - 1] = (voltage[recieve[0] - 1] << 8) | recieve[j];

        current[recieve[0] - 1] = 0;
        for (int j = 7; j < 11; j++)
          current[recieve[0] - 1] = (current[recieve[0] - 1] << 8) | recieve[j];

        power_prev[recieve[0] - 1] = power_next[recieve[0] - 1];
        power_next[recieve[0] - 1] = 0;
        for (int j = 11; j < 15; j++)
          power_next[recieve[0] - 1] = (power_next[recieve[0] - 1] << 8) | recieve[j];

        factor[recieve[0] - 1] = 0;
        for (int j = 15; j < 19; j++)
          factor[recieve[0] - 1] = (factor[recieve[0] - 1] << 8) | recieve[j];

        frequency[recieve[0] - 1] = 0;
        for (int j = 19; j < 23; j++)
          frequency[recieve[0] - 1] = (frequency[recieve[0] - 1] << 8) | recieve[j];

        consumption[recieve[0] - 1] = 0;
        for (int j = 23; j < 27; j++)
          consumption[recieve[0] - 1] = (consumption[recieve[0] - 1] << 8) | recieve[j];

        Serial.printf("%02X: ", recieve[0]);
        Serial.printf("%fV, ", voltage[recieve[0] - 1] / 1000.0);
        Serial.printf("%fA, ", current[recieve[0] - 1] / 1000.0);
        Serial.printf("%fW, ", power_next[recieve[0] - 1] / 1000.0);
        Serial.printf("%f, ", factor[recieve[0] - 1] / 10000.0);
        Serial.printf("%fHz, ", frequency[recieve[0] - 1] / 1000.0);
        Serial.printf("%fkW·h\n", consumption[recieve[0] - 1] / 10000.0);

        if (alarm_set[recieve[0] - 1] && power_next[recieve[0] - 1] > 500) {
          alarm_flag[recieve[0] - 1] = 1;
        } else {
          alarm_flag[recieve[0] - 1] = 0;
        }

        long power_sum = 0;
        long power_max = 0;
        int power_max_index;
        for (int i = 0; i < NUM; i++)
        {
          power_sum += power_next[i];
          if(power_max < power_next[i]) {
            power_max = power_next[i];
            power_max_index = i;
          }
        }
        Serial.printf("Total: %f, Limit: %f\n", power_sum / 1000.0, limit / 1000.0);
        if (power_sum > limit) {
          if(power_next[recieve[0] - 1] > limit)
            count_down[recieve[0] - 1] = 10;
          else if(power_sum - power_next[recieve[0] - 1] + power_prev[recieve[0] - 1] <= limit)
            count_down[recieve[0] - 1] = 10;
          else
            count_down[power_max_index] = 10;
        }
        portEXIT_CRITICAL_ISR(&timerMux);
      } else {
        Serial.printf("Modbus Length Error! Error Length: %d\n", recieve[2]);
      }
    } else {
      Serial.printf("Modbus Read Error! Error Code: %02X\n", recieve[1]);
    }
  }
  else
    Serial.printf("CRC16 Check Error! Remote: %02X%02X, Local: %04X\n", recieve[index - 2], recieve[index - 1], CRC16);
  // Give a semaphore that we can check in the loop
  xSemaphoreGiveFromISR(timerSemaphore, NULL);
  // It is safe to use digitalRead/Write here if you want to toggle an output
}

void setup()
{
  Serial.begin(115200);
  Serial2.begin(115200);
  delay(10);

  for (int i = 0; i < NUM; i++)
  {
    state_flag[i] = 1;
    pinMode(ports[i], OUTPUT);
    digitalWrite(ports[i], state_flag[i]);
  }

  pinMode(LED_BUILTIN, OUTPUT);
  pinMode(BUZZER_PORT, OUTPUT);
  blink_flag = 0;
  digitalWrite(LED_BUILTIN, blink_flag);
  digitalWrite(BUZZER_PORT, LOW);
  delay(1000);
  digitalWrite(BUZZER_PORT, HIGH);

  blink_flag = 0;
  digitalWrite(LED_BUILTIN, blink_flag);
  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid, password);
  WiFi.setAutoConnect(true);

  while (WiFi.status() != WL_CONNECTED)
    delay(500);

  UDP.begin(8080);

  // Create semaphore to inform us when the timer has fired
  timerSemaphore = xSemaphoreCreateBinary();

  // Use 1st timer of 4 (counted from zero).
  // Set 80 divider for prescaler (see ESP32 Technical Reference Manual for more
  // info).
  timer0 = timerBegin(0, 80, true);

  // Attach onTimer function to our timer.
  timerAttachInterrupt(timer0, &onTimer, true);

  // Set alarm to call onTimer function every second (value in microseconds).
  // Repeat the alarm (third parameter)
  timerAlarmWrite(timer0, 1000000 / NUM, true);

  for (int i = 0; i < NUM; i++) {
    clear_consumption[0] = i + 1;
    unsigned int CRC16 = ModBusCRC(clear_consumption, 6);
    clear_consumption[6] = (CRC16 & 0xFF00) >> 8;
    clear_consumption[7] = (CRC16 & 0x00FF) >> 0;

    Serial.printf("Modbus RTU Send: ");
    for (int i = 0; i < 7; i++) {
      Serial.printf("%02X ", clear_consumption[i]);
    }
    Serial.printf("%02X\n", clear_consumption[7]);

    Serial2.write((uint8_t *)clear_consumption, 8);
    delay(50);
    int index = 0;
    while (Serial2.available() > 0)
      recieve[index++] = Serial2.read();

    Serial.printf("Modbus RTU Receive: ");
    for (int i = 0; i < index - 1; i++) {
      Serial.printf("%02X ", recieve[i]);
    }
    Serial.printf("%02X\n", recieve[index - 1]);

    CRC16 = ModBusCRC(recieve, index - 2);
    if (CRC16 == (recieve[index - 2] << 8) | recieve[index - 1])
      Serial.printf("CRC16 Check Correct!\n");
    else
      Serial.printf("CRC16 Check Error! Remote: %02X%02X, Local: %04X\n", recieve[index - 2], recieve[index - 1], CRC16);
    delay(50);
  }

  // Start an alarm
  timerAlarmEnable(timer0);
  blink_flag = 1;
  digitalWrite(LED_BUILTIN, blink_flag);
}

void loop()
{
  // If Timer has fired
  if (xSemaphoreTake(timerSemaphore, 0) == pdTRUE) {
    int count_sum = 0;
    portENTER_CRITICAL(&timerMux);
    for (int i = 0; i < NUM; i++)
    {
      count_sum += count_down[i] + alarm_flag[i];
      if (count_down[i] > 0) {
        digitalWrite(ports[i], 0);
        count_down[i]--;
      } else {
        digitalWrite(ports[i], state_flag[i]);
      }
    }
    portEXIT_CRITICAL(&timerMux);
    if (count_sum > 0) {
      blink_flag = 0;
      digitalWrite(LED_BUILTIN, blink_flag);
      digitalWrite(BUZZER_PORT, LOW);
    } else {
      blink_flag = 1;
      digitalWrite(LED_BUILTIN, blink_flag);
      digitalWrite(BUZZER_PORT, HIGH);
    }
  }

  int packetSize = UDP.parsePacket(); //获取当前队首数据包长度
  if (packetSize) { // 有数据可用
    // Serial.printf("Received %d bytes from %s, port %d\n", packetSize, UDP.remoteIP().toString().c_str(), UDP.remotePort());
    int len = UDP.read(incomingPacket, 40); // 读取数据到incomingPacket
    if (len > 0) { // 如果正确读取
      incomingPacket[len] = 0; //末尾补0结束字符串
      // Serial.printf("UDP packet contents: %s\n", incomingPacket);

      if (incomingPacket[0] == 'Q' && incomingPacket[1] == 'M') {
        int address = 0;
        for (int i = 2; i < 4; i++) {
          address <<= 4;
          if (incomingPacket[i] >= '0' && incomingPacket[i] <= '9') {
            address |= incomingPacket[i] - '0';
          } else if (incomingPacket[i] >= 'A' && incomingPacket[i] <= 'F') {
            address |= incomingPacket[i] - 'A';
          } else if (incomingPacket[i] >= 'a' && incomingPacket[i] <= 'f') {
            address |= incomingPacket[i] - 'a';
          } else {
            address = 0;
            break;
          }
        }

        UDP.beginPacket(UDP.remoteIP(), UDP.remotePort());
        if (address == 0)
          UDP.print("E00\r\n");
        else {
          portENTER_CRITICAL(&timerMux);
          UDP.printf("U%08XI%08XP%08XA%01XT%01XS%01XC%01X\r\n", voltage[address - 1], current[address - 1], power_next[address - 1], alarm_flag[address - 1], alarm_set[address - 1], state_flag[address - 1], count_down[address - 1]);
          portEXIT_CRITICAL(&timerMux);
        }
        UDP.endPacket();
      } else if (incomingPacket[0] == 'Q' && incomingPacket[1] == 'F') {
        int address = 0;
        for (int i = 2; i < 4; i++) {
          address <<= 4;
          if (incomingPacket[i] >= '0' && incomingPacket[i] <= '9') {
            address |= incomingPacket[i] - '0';
          } else if (incomingPacket[i] >= 'A' && incomingPacket[i] <= 'F') {
            address |= incomingPacket[i] - 'A';
          } else if (incomingPacket[i] >= 'a' && incomingPacket[i] <= 'f') {
            address |= incomingPacket[i] - 'a';
          } else {
            address = 0;
            break;
          }
        }

        UDP.beginPacket(UDP.remoteIP(), UDP.remotePort());
        if (address == 0)
          UDP.print("E00\r\n");
        else {
          portENTER_CRITICAL(&timerMux);
          UDP.printf("U%08XI%08XP%08XF%08Xf%08XW%08XA%01XT%01XS%01XC%01X\r\n", voltage[address - 1], current[address - 1], power_next[address - 1], factor[address - 1], frequency[address - 1], consumption[address - 1], alarm_flag[address - 1], alarm_set[address - 1], state_flag[address - 1], count_down[address - 1]);
          portEXIT_CRITICAL(&timerMux);
        }
        UDP.endPacket();
      } else if (incomingPacket[0] == 'L' && incomingPacket[1] == 'T') {
        long limit_input = 0;
        for (int i = 2; i < 6; i++)
          if (incomingPacket[i] >= '0' && incomingPacket[i] <= '9')
            limit_input = limit_input * 10 + incomingPacket[i] - '0';
          else {
            limit_input = -1;
            break;
          }

        limit_input *= 1000;

        UDP.beginPacket(UDP.remoteIP(), UDP.remotePort());
        portENTER_CRITICAL(&timerMux);
        if (limit_input < 0)
          UDP.print("E02\r\n");
        else {
          limit = limit_input;
          UDP.print("OK\r\n");
        }
        portEXIT_CRITICAL(&timerMux);
        UDP.endPacket();
      } else if (incomingPacket[0] == 'A' && incomingPacket[1] == 'L') {
        int address = 0;
        for (int i = 2; i < 4; i++) {
          address <<= 4;
          if (incomingPacket[i] >= '0' && incomingPacket[i] <= '9') {
            address |= incomingPacket[i] - '0';
          } else if (incomingPacket[i] >= 'A' && incomingPacket[i] <= 'F') {
            address |= incomingPacket[i] - 'A';
          } else if (incomingPacket[i] >= 'a' && incomingPacket[i] <= 'f') {
            address |= incomingPacket[i] - 'a';
          } else {
            address = 0;
            break;
          }
        }

        UDP.beginPacket(UDP.remoteIP(), UDP.remotePort());
        if (address == 0)
          UDP.print("E00\r\n");
        else {
          portENTER_CRITICAL(&timerMux);
          alarm_set[address - 1] = 1;
          UDP.print("OK\r\n");
          portEXIT_CRITICAL(&timerMux);
        }
        UDP.endPacket();
      } else if (incomingPacket[0] == 'U' && incomingPacket[1] == 'L') {
        int address = 0;
        for (int i = 2; i < 4; i++) {
          address <<= 4;
          if (incomingPacket[i] >= '0' && incomingPacket[i] <= '9') {
            address |= incomingPacket[i] - '0';
          } else if (incomingPacket[i] >= 'A' && incomingPacket[i] <= 'F') {
            address |= incomingPacket[i] - 'A';
          } else if (incomingPacket[i] >= 'a' && incomingPacket[i] <= 'f') {
            address |= incomingPacket[i] - 'a';
          } else {
            address = 0;
            break;
          }
        }

        UDP.beginPacket(UDP.remoteIP(), UDP.remotePort());
        if (address == 0)
          UDP.print("E00\r\n");
        else {
          portENTER_CRITICAL(&timerMux);
          alarm_set[address - 1] = 0;
          UDP.print("OK\r\n");
          portEXIT_CRITICAL(&timerMux);
        }
        UDP.endPacket();
      } else if (incomingPacket[0] == 'S' && incomingPacket[1] == 'T') {
        int address = 0;
        for (int i = 2; i < 4; i++) {
          address <<= 4;
          if (incomingPacket[i] >= '0' && incomingPacket[i] <= '9') {
            address |= incomingPacket[i] - '0';
          } else if (incomingPacket[i] >= 'A' && incomingPacket[i] <= 'F') {
            address |= incomingPacket[i] - 'A';
          } else if (incomingPacket[i] >= 'a' && incomingPacket[i] <= 'f') {
            address |= incomingPacket[i] - 'a';
          } else {
            address = 0;
            break;
          }
        }

        UDP.beginPacket(UDP.remoteIP(), UDP.remotePort());
        if (address == 0)
          UDP.print("E00\r\n");
        else {
          portENTER_CRITICAL(&timerMux);
          state_flag[address - 1] = 1;
          UDP.print("OK\r\n");
          portEXIT_CRITICAL(&timerMux);
        }
        UDP.endPacket();
      } else if (incomingPacket[0] == 'C' && incomingPacket[1] == 'L') {
        int address = 0;
        for (int i = 2; i < 4; i++) {
          address <<= 4;
          if (incomingPacket[i] >= '0' && incomingPacket[i] <= '9') {
            address |= incomingPacket[i] - '0';
          } else if (incomingPacket[i] >= 'A' && incomingPacket[i] <= 'F') {
            address |= incomingPacket[i] - 'A';
          } else if (incomingPacket[i] >= 'a' && incomingPacket[i] <= 'f') {
            address |= incomingPacket[i] - 'a';
          } else {
            address = 0;
            break;
          }
        }

        UDP.beginPacket(UDP.remoteIP(), UDP.remotePort());
        if (address == 0)
          UDP.print("E00\r\n");
        else {
          portENTER_CRITICAL(&timerMux);
          state_flag[address - 1] = 0;
          UDP.print("OK\r\n");
          portEXIT_CRITICAL(&timerMux);
        }
        UDP.endPacket();
      } else {
        UDP.beginPacket(UDP.remoteIP(), UDP.remotePort());
        UDP.print("E01\r\n");
        UDP.endPacket();
      }
    }
  }
}
