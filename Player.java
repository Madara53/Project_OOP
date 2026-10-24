import java.awt.Color;
import java.awt.Graphics2D;

public class Player {
    public  int W = 50;
    public  int H = 50;

    private double x, y;
    private Color color = Color.BLUE;

    public Player(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public void draw(Graphics2D g) {
        
        g.setColor(color);
        g.fillRoundRect((int) x, (int) y, W, H, 8, 8); // วาดตัวละครเป็นสี่เหลี่ยมมน

       
        // วาดตา
        g.setColor(Color.WHITE);
        g.fillOval((int) x + 10, (int) y + 10, 10, 10); // วาดตาซ้าย
        g.fillOval((int) x + 30, (int) y + 10, 10, 10); // วาดตาขวา


        g.setColor(Color.RED);
        g.drawString("Player", (int) x + 8, (int) y - 8); // วาดข้อความ "Player" 

        g.setColor(Color.RED);
        g.drawArc((int) x+20, (int) y+20, 10, 10, 200, 140); // วาดปาก


        g.setColor(Color.red);
        g.fillRect((int) x + 2, (int) y - 4, W - 4, 5); // วาดหมวก


      
    }
}