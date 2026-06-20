# Issues

# Major issues
- The app does not detect a camera that is connected befor launching the app; it only detects one that is connected after the launch.
- Dialogues pop up again when the app is re-launched or the camera is re-connected.
- The video from a single camera is displayed in two overlapped boxes.
  - One of the box does not have the flip persistence functionality. When re-connected or re-launched, the video on the box is not flipped. Turning off the flip and flipping again fixes this.
  - Another box has the flip persistence functionality. 

# Minor issues
- The app shows two dialogue. Remove this issue this is an expected behavior rather than an issue.


- アプリの起動後に接続したカメラを検出するが、起動前にカメラが接続されてると何も起こらない
- カメラを接続すると、許可に関する2つのダイアログが現れる
- アプリを再起動した際に再度ダイアログの操作が必要
- カメラが2つのオーバーラップしたボックスに表示されている
    - 片方のボックスはフリップの保存がうまく機能していない：再起動・再接続時にフリップがオンになっていても、そのボックスの画像はフリップされていない。フリップを外して再度フリップをオンにすると適用される
    - もう片方のボックスはフリップの保存が機能している