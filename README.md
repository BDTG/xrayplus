# XrayPlus

Mod **Xray client-side** cho Minecraft **1.20.1 / Forge 47.x**, được dựng riêng cho modpack
**Soulrend – Dark Fantasy RPG Soulslike** (pack dùng Embeddium + Oculus, nên XrayPlus hook vào cả
đường render của vanilla lẫn của Embeddium).

> ⚠️ Chỉ dùng cho **single-player** hoặc **server của chính bạn**. Dùng Xray trên server công khai
> gần như chắc chắn vi phạm luật server và có thể bị ban. Mod không gửi packet nào lên server, không
> sửa dữ liệu thế giới — nó chỉ thay đổi những gì *máy bạn vẽ ra*.

---

## Tính năng

| Tính năng | Mô tả |
|---|---|
| Ẩn mọi khối không nằm trong danh sách | Chỉ còn quặng / rương / spawner… hiện ra, địa hình trong suốt |
| Fullbright | Ghi đè lightmap thành trắng → thấy quặng trong hang tối, ban đêm, không cần torch |
| 4 nhóm mặc định | `ores`, `containers`, `spawners`, `portals` — bật/tắt từng nhóm |
| Tự dò quặng | Block nào có id chứa `_ore` / `raw_..._block` sẽ tự động được thêm (bắt được quặng của mod) |
| Tag của Forge | Đọc `forge:ores`, `c:ores` → quặng của mod nào có tag là hiện |
| `/xray` command | thêm/xoá block, bật/tắt nhóm, reload config ngay trong game |
| GUI cấu hình | `F9` hoặc Mods → XrayPlus → Config |
| HUD nhỏ | Góc trên trái hiện `XRAY ores+containers` khi đang bật |
| Config JSON | `config/xrayplus.json`, sửa tay được, có `/xray reload` |

## Yêu cầu

- Minecraft **1.20.1**
- Forge **47.x** (đã test với 47.4.26 — bản mà Soulrend v8.5.2 dùng)
- Client. Không cần cài trên server; mod khai báo `clientSideOnly` + `displayTest = IGNORE_ALL_VERSION`
  nên vào server vanilla/Forge bình thường không bị kick vì thiếu mod.

## Cài đặt

1. Tải `xrayplus-1.0.0.jar` (build từ source hoặc Release).
2. Bỏ vào `.../instances/Soulrend - Dark Fantasy RPG Soulslike/minecraft/mods/`.
3. Khởi động lại game.

Trong game:

| Phím | Việc |
|---|---|
| `` ` `` (phím cạnh số 1) | Bật/tắt Xray |
| `F9` | Mở bảng cấu hình |

Phím đổi được ở **Options → Controls → Key Binds → XrayPlus** (mặc định không đè phím của mod nào
trong pack — `X` đã bị 4 mod khác giành).

## Lệnh

```
/xray                     bật/tắt
/xray on | off
/xray status              trạng thái + số block đang hiện
/xray list                liệt kê block đang hiện
/xray groups              trạng thái các nhóm
/xray group <id> on|off   bật/tắt 1 nhóm (ores, containers, spawners, portals)
/xray add <block_id>      luôn hiện block này
/xray remove <block_id>   luôn ẩn block này
/xray fullbright on|off
/xray hud on|off
/xray heuristic on|off
/xray reload              đọc lại config/xrayplus.json
/xray reset               về mặc định
```

## Cấu hình

`config/xrayplus.json` được tạo ở lần chạy đầu:

```json
{
  "enabled": false,
  "fullbright": true,
  "hud": true,
  "nameHeuristic": true,
  "groups": [
    {
      "id": "ores",
      "enabled": true,
      "tags": ["forge:ores", "c:ores"],
      "blocks": ["minecraft:ancient_debris", "minecraft:amethyst_cluster"]
    },
    {
      "id": "containers",
      "enabled": true,
      "tags": [],
      "blocks": ["minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel"]
    }
  ],
  "extraBlocks": [],
  "blacklist": []
}
```

Muốn thêm quặng của mod khác: `/xray add <modid:block>` — không cần sửa file.

## Cách hoạt động

Ba mixin, tất cả đều client-side:

1. `ModelBlockRendererMixin` — chặn `tesselateWithAO` / `tesselateWithoutAO` (bản overload
   `ModelData + RenderType` do Forge thêm). Đây là điểm mà cả vanilla **và** `ForgeBlockRenderer`
   của Embeddium gọi vào, nên một hook phủ được cả hai đường.
2. `EmbeddiumBlockRendererMixin` — Embeddium (Sodium) tự dựng mesh trong
   `pipeline/BlockRenderer#renderModel` và **không** đi qua `tesselateBlock`; hook thứ hai chặn
   đúng chỗ đó. Hook đọc `BlockRenderContext#state()` bằng reflection (không phụ thuộc compile-time
   vào Embeddium) và `@Pseudo` để pack không có Embeddium vẫn load bình thường.
3. `LightTextureMixin` — với Xray bật, ghi 16×16 pixel của lightmap thành trắng và upload. Cách này
   không phụ thuộc renderer nào (vanilla, Embeddium, Oculus đều sample cùng một texture lightmap).

Bật/tắt sẽ gọi `LevelRenderer#allChanged()` để dựng lại chunk mesh ngay (Embeddium cũng hook hàm này),
nên không cần F3+A bằng tay.

## Giới hạn đã biết

- **Occlusion culling**: cả vanilla lẫn Embeddium đều bỏ qua những *section* 16×16×16 bị đá bịt kín
  hoàn toàn; Xray chỉ ẩn khối, không tắt culling. Vì vậy quặng nằm trong section kín mít có thể không
  hiện cho tới khi bạn lại gần/đứng trong hang. Đứng trong hang hoặc trong mỏ thì thấy đầy đủ.
- **Shader pack (Oculus)**: fullbright dùng lightmap nên vẫn ăn với phần lớn shader, nhưng một số
  shader tự tính ánh sáng từ shadow map — khi đó bật/tắt shader sẽ thấy khác nhau.
- Không ẩn chất lỏng (nước/dung nham) vì chúng đi qua `LiquidBlockRenderer`/`FluidRenderer` riêng.
- Không có "cave finder"/"chỉ hiện block trong suốt" — cố tình giữ mod nhỏ và ít xung đột.

## Build từ source

Cần JDK 17 và mạng (lần build đầu tải Forge MDK + mappings, ~5–10 phút).

```bash
./gradlew build
# jar thành phẩm: build/reobfJar/output.jar
```

`reobfJar` là bản đã remap sang SRG — **đây là jar dùng để chơi**, không dùng `build/libs/*-dev.jar`.

## Cấu trúc

```
src/main/java/com/bdtg/xrayplus/
├── XrayPlus.java              # @Mod entry
├── client/                    # keybind, command, HUD, config screen, world refresh
├── config/XrayConfig.java     # JSON config
├── core/XrayState.java        # tập block đang hiện + quyết định ẩn/hiện
├── core/SodiumCompat.java     # đọc state từ Embeddium context (reflection)
└── mixin/                     # 3 mixin kể trên
```

## License

MIT — xem [LICENSE](LICENSE).
