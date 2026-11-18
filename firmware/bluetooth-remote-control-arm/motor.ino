// ── 馬達腳位 ──
int A_1A = 5;   // 左輪 PWM (ENA)
int A_1B = 6;   // 左輪 IN1
int B_1A = 9;   // 右輪 PWM (ENB)
int B_1B = 10;  // 右輪 IN2

// 馬達平衡校正（移植自 robot_basic_followline）
const int LEFT_MOTOR_PCT  = 100;
const int RIGHT_MOTOR_PCT = 100;

void motorSetup() {
  // 先清除輸出 latch，再切換方向，避免上電瞬間誤動作
  digitalWrite(A_1A, LOW);
  digitalWrite(A_1B, LOW);
  digitalWrite(B_1A, LOW);
  digitalWrite(B_1B, LOW);
  pinMode(A_1A, OUTPUT);
  pinMode(A_1B, OUTPUT);
  pinMode(B_1A, OUTPUT);
  pinMode(B_1B, OUTPUT);
}

// ── 底層馬達抽象（移植自 robot_basic_followline）──

void MotorLeft(bool fwd, int level) {
  int adj = (int)((long)level * LEFT_MOTOR_PCT / 100);
  analogWrite(A_1A, fwd ? adj : 0);
  analogWrite(A_1B, fwd ? 0   : adj);
}

void MotorRight(bool fwd, int level) {
  int adj = (int)((long)level * RIGHT_MOTOR_PCT / 100);
  analogWrite(B_1A, fwd ? adj : 0);
  analogWrite(B_1B, fwd ? 0   : adj);
}

// ── 高層動作函式（移植自 robot_basic_followline）──

void Forward(int level) {
  MotorLeft(true, level);
  MotorRight(true, level);
}

void Backward(int level) {
  MotorLeft(false, level);
  MotorRight(false, level);
}

void SpinLeft(int level) {
  MotorLeft(false, level);   // 左輪後退
  MotorRight(true, level);   // 右輪前進
}

void SpinRight(int level) {
  MotorLeft(true, level);    // 左輪前進
  MotorRight(false, level);  // 右輪後退
}

void Stop() {
  analogWrite(A_1A, 0);
  analogWrite(A_1B, 0);
  analogWrite(B_1A, 0);
  analogWrite(B_1B, 0);
  digitalWrite(A_1A, LOW);
  digitalWrite(A_1B, LOW);
  digitalWrite(B_1A, LOW);
  digitalWrite(B_1B, LOW);
}

// ── 差速混控直接驅動（M<左輪 PWM>,<右輪 PWM>，負數為反轉；支援 iOS / Android 類比搖桿）──
void DriveDifferential(int leftSpeed, int rightSpeed) {
  if (leftSpeed == 0 && rightSpeed == 0) {
    Stop();
    return;
  }
  MotorLeft(leftSpeed >= 0, constrain(abs(leftSpeed), 0, 255));
  MotorRight(rightSpeed >= 0, constrain(abs(rightSpeed), 0, 255));
}
