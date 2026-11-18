#include <SoftwareSerial.h>
#include <Servo.h>


const int BT_RX = 13; // Arduino 接收腳 (接 BT06 的 TXD)
const int BT_TX = 12; // Arduino 發送腳 (接 BT06 的 RXD)
SoftwareSerial BT(BT_RX, BT_TX);

char command;               // 指令碼
int speedVal = 100;         // 預設速度
char currentAction = 'S';   // 目前行進狀態 ('F', 'B', 'L', 'R', 'S')

// 依據目前狀態與速度更新馬達輸出
void updateMovement() {
  if (speedVal == 0 || currentAction == 'S') {
    Stop();
    Serial.println(F("[動作] 停止 (Stop)"));
    return;
  }
  switch (currentAction) {
    case 'F': 
      Forward(speedVal);   
      Serial.print(F("[動作] 前進 (Forward), 速度: "));
      Serial.println(speedVal);
      break; 
    case 'B': 
      Backward(speedVal);  
      Serial.print(F("[動作] 後退 (Backward), 速度: "));
      Serial.println(speedVal);
      break; 
    case 'L': 
      SpinLeft(speedVal);  
      Serial.print(F("[動作] 原地左轉 (SpinLeft), 速度: "));
      Serial.println(speedVal);
      break; 
    case 'R': 
      SpinRight(speedVal); 
      Serial.print(F("[動作] 原地右轉 (SpinRight), 速度: "));
      Serial.println(speedVal);
      break; 
    default:  
      Stop();              
      Serial.println(F("[動作] 停止 (Stop)"));
      break; 
  }
}

// ── 機械手臂伺服馬達 ──
Servo baseServo;     // 1 號伺服馬達：底座旋轉
Servo shoulderServo; // 2 號伺服馬達：大臂升降
Servo elbowServo;    // 3 號伺服馬達：小臂伸展
Servo gripperServo;  // 4 號伺服馬達：爪子開合

// ── 機械手臂腳位定義 ──
const int BASE_PIN = 3;
const int SHOULDER_PIN = 11;
const int ELBOW_PIN = 8;
const int GRIPPER_PIN = 4;

// ── 馬達腳位與平衡校正已定義於 motor.ino ──

void setup() {
  Serial.begin(115200);
  delay(100);

  

  BT.begin(9600);
  BT.setTimeout(30); // M<左>,<右> 讀到換行最多等 30ms
  
  // 馬達初始化並強制保持靜止
  motorSetup();
  Stop();
  currentAction = 'S';

  // 伺服馬達初始化，並設定預設角度 90 度
  baseServo.attach(BASE_PIN);
  baseServo.write(90);

  shoulderServo.attach(SHOULDER_PIN);
  shoulderServo.write(90);

  elbowServo.attach(ELBOW_PIN);
  elbowServo.write(90);

  gripperServo.attach(GRIPPER_PIN);
  gripperServo.write(90);

  Serial.println(F("控制系統初始化完畢！等待藍牙連線與指令..."));
}

void loop() {
  if (BT.available() > 0) {
    command = BT.read();

    Serial.print(F("[收到藍牙指令]: '"));
    Serial.print(command);
    Serial.print(F("' (ASCII: "));
    Serial.print((int)command);
    Serial.println(F(")"));

    // 自動小寫轉大寫容錯
    if (command >= 'a' && command <= 'z') {
      command = command - 'a' + 'A';
    }
    
    // 1. 判斷是否為速度指令 (字元 '0' 或 '1' 到 '9')
    if (command == '0') {
      speedVal = 0;
      Serial.println(F("[速度設定] 0 (煞車)"));
      Stop();
      
    } else if (command >= '1' && command <= '9') {
      int speedLevel = command - '0'; 
      speedVal = map(speedLevel, 1, 9, 100, 220); 
      Serial.print(F("[速度設定] 等級 "));
      Serial.print(speedLevel);
      Serial.print(F(" -> PWM: "));
      Serial.println(speedVal);
      // 若目前為靜止狀態 ('S')，僅更新速度數值，絕不啟動馬達！
      // 必須收到移動訊號 ('F','B','L','R') 後才依此速度前進！
      if (currentAction != 'S') {
        updateMovement();
      } else {
        Stop();
      }
    }

    // 2. 'M' 開頭有兩種指令，讀前 4 個字元區分：
    //   舵機：M<編號 1-4><三位數角度>，例如 M1090（固定 4 個數字、無換行）
    //   差速：M<左輪 PWM>,<右輪 PWM> + 換行，例如 M200,-150（有逗號；左輪佔 4 個字元時一定是負號開頭）
    else if (command == 'M') {
      unsigned long startMs = millis();
      // 等待後續的 4 個字元到達
      while (BT.available() < 4) {
        if (millis() - startMs > 50) {
          break; // 逾時防鎖死
        }
      }

      char buf[5] = {0};
      if (BT.available() >= 4) {
        for (int i = 0; i < 4; i++) buf[i] = BT.read();
      }

      if (buf[0] != 0 && (strchr(buf, ',') != NULL || buf[0] == '-')) {
        String line = buf;
        if (strchr(buf, '\n') == NULL) line += BT.readStringUntil('\n');
        int comma = line.indexOf(',');
        if (comma != -1) {
          int leftVal = line.substring(0, comma).toInt();
          int rightVal = line.substring(comma + 1).toInt();
          DriveDifferential(leftVal, rightVal);
          currentAction = (leftVal == 0 && rightVal == 0) ? 'S' : 'D'; // 'D'：速度數字指令不會重新啟動舊的 F/B/L/R 動作
          Serial.print(F("[動作] 搖桿 左輪 "));
          Serial.print(leftVal);
          Serial.print(F(" 右輪 "));
          Serial.println(rightVal);
        }
      } else if (buf[0] != 0) {
        char servoChar = buf[0];
        char a1 = buf[1];
        char a2 = buf[2];
        char a3 = buf[3];
        
        int servoNum = servoChar - '0';
        int angle = (a1 - '0') * 100 + (a2 - '0') * 10 + (a3 - '0');
        
        if (angle >= 0 && angle <= 180) {
          Serial.print(F("[手臂舵機] 舵機 "));
          Serial.print(servoNum);
          Serial.print(F(" 移動至角度: "));
          Serial.println(angle);
          
          switch(servoNum) {
            case 1: baseServo.write(angle);     break;
            case 2: shoulderServo.write(angle); break;
            case 3: elbowServo.write(angle);    break;
            case 4: gripperServo.write(angle);  break;
          }
        }
      } else {
        // 逾時且資料不全，清空當前殘留避免後續指令位移
        while (BT.available() > 0) {
          BT.read();
        }
      }
    }

    // 3. 判斷是否為方向指令 ('F', 'B', 'L', 'R', 'S')
    else if (command == 'F' || command == 'B' || command == 'L' || command == 'R' || command == 'S') {
      currentAction = command;
      updateMovement();
    }
  }
}

