# HaoHanSMP-datapack

Một datapack dành riêng cho server HaoHanSMP.

## Cách sử dụng

> **Lưu ý:** Datapack này được thiết kế cho **Minecraft Java Edition** với pack format **94.1** (tức là phiên bản 1.21.11).
> 
> **Resource Pack:** Để hiển thị đầy đủ kết cấu (textures) và mô hình (models) của các vật phẩm/khối tùy chỉnh, bạn cần cài đặt thêm [Resource Pack](https://github.com/Hao-Han-SMP/HaoHanSMP-resourcepack/releases) tương ứng.
> 
> **Plugin:** Phải cài đặt plugin [HaoHanLunar](https://github.com/Hao-Han-SMP/HaoHanLunar) để có trải nghiệm tốt nhất.

1. Tải file `HaoHanSMP-datapack.zip` từ phần [Releases](../../releases) hoặc tự build theo [hướng dẫn ở dưới](#build--package).
2. Mở Minecraft, vào màn hình **Create New World** (hoặc đặt file vào thư mục `datapacks/` của world đã có).
3. Chọn **Data Packs** → **Open Pack Folder**, kéo file `.zip` vào.
4. Bật datapack và tạo world.

## Build / Package

Datapack cung cấp sẵn script đóng gói cho cả Linux/macOS và Windows. File `.zip` đầu ra sẽ nằm trong thư mục `out/`.

### Windows (PowerShell)

```powershell
.\package.ps1
```

### Linux / macOS

```bash
chmod +x package.sh   # chỉ cần chạy lần đầu
./package.sh
```

> Yêu cầu: Lệnh `zip` phải được cài sẵn trên hệ thống.

## Giấy phép

Dự án được phát hành theo giấy phép [MIT](LICENSE).

## Cảm ơn 

(Bổ sung sau) 
