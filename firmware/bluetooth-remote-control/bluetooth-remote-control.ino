#include <SoftwareSerial.h>


const int BT_RX = 13; // Arduino 接收腳 (接 BT06 的 TXD)
const int BT_TX = 12; // Arduino 發送腳 (接 BT06 的 RXD)
SoftwareSerial BT(BT_RX, BT_TX);

const int LED_STATUS = 13; // 板載指示燈 (Pin 13)

char command;               // 指令碼
int speedVal = 100;         // 預設速度 (100~200)
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

void setup() {
  Serial.begin(115200);
  delay(100);

  // 啟動 BT06 軟體序列埠
  BT.begin(9600);
  BT.setTimeout(30); // M<左>,<右> 讀到換行最多等 30ms

  // 初始化狀態指示燈
  if (BT_RX != LED_STATUS && BT_TX != LED_STATUS) {
    pinMode(LED_STATUS, OUTPUT);
    // 閃爍 3 次表示開機正常
    for (int i = 0; i < 3; i++) {
      digitalWrite(LED_STATUS, HIGH);
      delay(100);
      digitalWrite(LED_STATUS, LOW);
      delay(100);
    }
  }
  
  // 馬達初始化並強制保持靜止
  motorSetup();
  Stop();
  currentAction = 'S';

  Serial.println(F(">> 系統已就緒，車輛初始為靜止狀態，等待手機發送移動指令..."));
}

void loop() {
  if (BT.available() > 0) {
    command = BT.read();

    // 收到字元時，立刻在電腦 Serial Monitor 印出，方便 100% 除錯！
    Serial.print(F("[收到藍牙指令]: '"));
    Serial.print(command);
    Serial.print(F("' (ASCII 碼: "));
    Serial.print((int)command);
    Serial.println(F(")"));

    // 自動轉大寫 (容錯小寫輸入)
    if (command >= 'a' && command <= 'z') {
      command = command - 'a' + 'A';
    }
    
    // 1. 速度指令 (字元 '0' 或 '1' 到 '9')
    if (command == '0') {
      speedVal = 0;
      Serial.println(F("[速度設定] 0 (煞車)"));
      Stop();
      
    } else if (command >= '1' && command <= '9') {
      int speedLevel = command - '0'; 
      speedVal = map(speedLevel, 1, 9, 100, 200); 
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

    // 差速混控指令 (M<左輪 PWM>,<右輪 PWM> + 換行，例如 M200,-150)
    else if (command == 'M') {
      String line = BT.readStringUntil('\n');
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
    }

    // 2. 方向指令 ('F', 'B', 'L', 'R', 'S')
    else if (command == 'F' || command == 'B' || command == 'L' || command == 'R' || command == 'S') {
      currentAction = command;
      updateMovement();
    }
  }
}
