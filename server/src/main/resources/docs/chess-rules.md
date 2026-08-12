# CƠ SỞ TRI THỨC CỜ VUA CHO HỆ THỐNG RAG (CHESS KNOWLEDGE BASE)

## 1. GIỚI THIỆU VỀ CỜ VUA (INTRODUCTION TO CHESS)

Cờ vua là một trò chơi bàn cờ chiến lược dành cho hai người chơi, được chơi trên một bảng hình vuông chia thành 64 ô màu
sáng và tối xen kẽ (8x8). Mỗi người chơi bắt đầu với 16 quân cờ: 1 Vua, 1 Hậu, 2 Xe, 2 Tượng, 2 Mã và 8 Tốt. Mục tiêu
của trò chơi là chiếu mat (checkmate) Vua của đối phương, tức là đưa Vua đối phương vào thế bị tấn công không thể chạy
thoát.

## 2. CÁCH DI CHUYỂN CỦA CÁC QUÂN CỜ (PIECE MOVEMENTS)

- **Vua (King):** Di chuyển 1 ô theo mọi hướng (ngang, dọc, chéo). Vua không thể đi vào ô đang bị quân đối phương kiểm
  soát.
- **Hậu (Queen):** Quân mạnh nhất. Có thể di chuyển bao nhiêu ô tùy ý theo hàng ngang, hàng dọc, hoặc đường chéo.
- **Xe (Rook):** Di chuyển bao nhiêu ô tùy ý theo hàng ngang hoặc hàng dọc.
- **Tượng (Bishop):** Di chuyển bao nhiêu ô tùy ý theo đường chéo. Mỗi Tượng chỉ đi trên các ô cùng màu với ô ban đầu
  của nó.
- **Mã (Knight):** Di chuyển theo hình chữ "L" (2 ô theo một hướng và 1 ô vuông góc, hoặc 1 ô theo một hướng và 2 ô
  vuông góc). Mã là quân duy nhất có thể nhảy qua đầu các quân khác.
- **Tốt (Pawn):** Ở nước đi đầu tiên, Tốt có thể tiến 1 hoặc 2 ô về phía trước. Ở các nước đi sau, Tốt chỉ được tiến 1
  ô. Tốt bắt quân đối phương bằng cách đi chéo 1 ô về phía trước.

## 3. CÁC LUẬT ĐẶC BIỆT (SPECIAL RULES)

- **Nhập thành (Castling):** Đây là nước đi đặc biệt liên quan đến Vua và Xe, giúp bảo vệ Vua và đưa Xe vào tham gia tấn
  công. Vua di chuyển 2 ô về phía Xe, và Xe nhảy qua Vua đứng vào ô bên cạnh. Điều kiện: Vua và Xe chưa từng di chuyển,
  không có quân nào ở giữa chúng, và Vua không bị chiếu hoặc không đi qua ô bị kiểm soát.
- **Bắt Tốt qua đường (En Passant):** Nếu một con Tốt đối phương di chuyển 2 ô từ vị trí ban đầu và đứng ngang hàng với
  Tốt của bạn, bạn có quyền bắt con Tốt đó như thể nó chỉ đi 1 ô. Nước đi này chỉ được thực hiện ngay lập tức sau khi
  đối phương đi Tốt 2 ô.
- **Phong cấp (Promotion):** Khi một con Tốt tiến đến hàng cuối cùng của đối phương, nó có thể được phong cấp thành bất
  kỳ quân nào (Hậu, Xe, Tượng, hoặc Mã) ngoại trừ Vua. Thông thường, Tốt được phong thành Hậu.

## 4. CÁC GIAI ĐOẠN CỦA VÁN ĐẤU (PHASES OF THE GAME)

- **Khai cuộc (Opening):** Giai đoạn đầu, mục tiêu là kiểm soát trung tâm, phát triển quân (đưa Mã, Tượng ra ngoài) và
  đưa Vua đến nơi an toàn (thường bằng cách nhập thành).
- **Trung cuộc (Middlegame):** Giai đoạn giữa, nơi xảy ra các cuộc giao tranh chính, lên kế hoạch chiến thuật và tấn
  công.
- **Tàn cuộc (Endgame):** Giai đoạn cuối khi trên bàn cờ còn ít quân. Vai trò của Vua trở nên quan trọng hơn và Tốt
  thường được tìm cách đưa xuống cuối bàn để phong cấp.

## 5. MỘT SỐ KHAI CUỘC PHỔ BIẾN (POPULAR OPENINGS)

- **Ruy Lopez (Ván cờ Tây Ban Nha):** `1.e4 e5 2.Nf3 Nc6 3.Bb5`. Khai cuộc kinh điển nhằm gây áp lực lên Mã c6 và kiểm
  soát trung tâm.
- **Phòng thủ Sicilian (Sicilian Defense):** `1.e4 c5`. Đen đáp trả bằng Tốt cánh c thay vì e5, tạo ra thế trận không
  đối xứng và phản công sắc bén.
- **Giuoco Piano (Ván cờ Ý):** `1.e4 e5 2.Nf3 Nc6 3.Bc4`. Tập trung phát triển Tượng nhanh chóng nhắm vào ô f7 yếu ớt
  của Đen.
- **Gambit Hậu (Queen's Gambit):** `1.d4 d5 2.c4`. Trắng hy sinh tạm thời một Tốt để giành quyền kiểm soát trung tâm.

## 6. CHIẾN THUẬT CƠ BẢN (BASIC TACTICS)

- **Ghim (Pin):** Một quân tấn công một quân đối phương, khiến quân đó không thể di chuyển vì nếu di chuyển sẽ để lộ một
  quân quan trọng hơn (như Vua hoặc Hậu) ở phía sau.
- **Xiên (Skewer):** Tương tự như Ghim, nhưng quân có giá trị cao hơn nằm phía trước. Bắt buộc quân giá trị cao phải di
  chuyển, để lộ quân yếu hơn phía sau cho đối phương bắt.
- **Chĩa đôi / Bắt đôi (Fork):** Một quân cùng lúc tấn công hai hoặc nhiều quân của đối phương. Mã là quân thường xuyên
  tạo ra các đòn chĩa đôi nguy hiểm.