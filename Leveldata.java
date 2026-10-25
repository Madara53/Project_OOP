import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * ข้อมูลด่าน (พื้น หนาม พื้นหาย กำแพง ปุ่ม ประตู)
 * ใช้ร่วมกันทั้งฝั่ง Server (คำนวณฟิสิกส์) และ Client (วาดภาพ)
 * จึงต้องส่งผ่านเครือข่ายแค่เลขด่านกับสถานะที่เปลี่ยนไป ไม่ต้องส่งทั้งแผนที่
 */
public class Leveldata {

    public static final int FW = 760, FH = 340;     // ขนาดพื้นที่เล่น
    public static final int TOTAL_LEVELS = 3;
    public static final int MAX_PLAYERS = 4;

    public static final Color[] COLORS = {
        new Color(235, 100, 125),   // P1 ชมพู
        new Color(60, 175, 120),    // P2 เขียว
        new Color(70, 140, 230),    // P3 ฟ้า
        new Color(245, 170, 50)     // P4 ส้ม
    };

    /** พื้นหาย: หายไปหลังมีคนเหยียบ */
    public static class VFloor {
        public Rectangle r;
        public int timer = -1;         // -1 = ยังไม่มีใครเหยียบ
        public boolean gone = false;
        public VFloor(Rectangle r) { this.r = r; }
    }

    public List<Rectangle> platforms = new ArrayList<>();
    public List<Rectangle> spikes = new ArrayList<>();
    public List<VFloor> vfloors = new ArrayList<>();
    public List<Rectangle> gates = new ArrayList<>();     // กำแพงที่เปิดเมื่อมีคนเหยียบปุ่ม
    public List<Rectangle> buttons = new ArrayList<>();
    public Rectangle door;
    public int spawnX, groundY;

    private static Rectangle block(int x, int top, int w) { return new Rectangle(x, top, w, FH + 60 - top); }
    private static Rectangle plank(int x, int top, int w) { return new Rectangle(x, top, w, 12); }
    private static Rectangle spike(int x, int groundTop, int w) { return new Rectangle(x, groundTop - 14, w, 14); }

    public static Leveldata build(int n) {
        Leveldata L = new Leveldata();
        switch (n) {
            case 1:
                L.spawnX = 30; L.groundY = 260;
                L.platforms.add(block(0, 260, 230));
                L.vfloors.add(new VFloor(block(230, 260, 60)));
                L.platforms.add(block(290, 260, 150));
                L.spikes.add(spike(330, 260, 60));
                L.platforms.add(block(440, 210, 80));
                L.platforms.add(plank(540, 150, 80));
                L.platforms.add(block(640, 100, 120));
                L.door = new Rectangle(700, 62, 36, 38);
                break;
            case 2:
                L.spawnX = 30; L.groundY = 250;
                L.platforms.add(block(0, 250, 230));
                L.spikes.add(spike(170, 250, 50));
                L.vfloors.add(new VFloor(block(230, 250, 70)));
                L.platforms.add(block(300, 250, 150));
                L.spikes.add(spike(340, 250, 50));
                L.platforms.add(block(460, 190, 60));
                L.platforms.add(plank(540, 140, 80));
                L.platforms.add(block(640, 90, 120));
                L.door = new Rectangle(700, 52, 36, 38);
                break;
            default:
                L.spawnX = 30; L.groundY = 250;
                L.platforms.add(block(0, 250, 260));
                L.buttons.add(new Rectangle(190, 244, 40, 6));        // ปุ่ม A (ก่อนกำแพง)
                L.gates.add(new Rectangle(260, 140, 16, 200));        // กำแพงกั้น
                L.platforms.add(block(276, 250, 164));
                L.buttons.add(new Rectangle(380, 244, 40, 6));        // ปุ่ม B (หลังกำแพง)
                L.vfloors.add(new VFloor(block(440, 250, 60)));
                L.platforms.add(block(500, 250, 120));
                L.spikes.add(spike(540, 250, 50));
                L.platforms.add(block(630, 190, 60));
                L.platforms.add(block(710, 130, 50));
                L.door = new Rectangle(716, 92, 36, 38);
                break;
        }
        return L;
    }
}