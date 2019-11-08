
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;


/**
 * 3D demo test using only integers :) (fixed-point math)
 * 
 * @author Leonardo Ono (ono.leo@gmail.com)
 */
public class View extends JPanel {
    
    private static final int SCREEN_WIDTH = 320;
    private static final int SCREEN_HEIGHT = 200;
    private static final int SCREEN_SCALE = 2;

    private final BufferedImage frameBuffer;
    private final int[] screen;
    
    // timer for main loop
    private final Timer timer = new Timer();
    
    // distance between camera and plane 
    // screen size 320x200 px, field of view = 60 deg.
    private final int d = 277;
    
    private final int floorCameraY = 100;
    private int floorCameraX = 0;
    private int floorCameraZ = 0;

    int angle = 0;
    int angle2 = 0;
    
    private final int[][] cubeLocalPoints = {
            {-100,  100,  100},
            { 100,  100,  100},
            { 100, -100,  100},
            {-100, -100,  100},
            {-100,  100, -100},
            { 100,  100, -100},
            { 100, -100, -100},
            {-100, -100, -100},
        };
    
    private final int[][] cubeScreenPoints = new int[8][2];
    private final int[][] cubeShadowScreenPoints = new int[8][2];
    
    // triangles {vertex_0, vertex_1, vertex_2, min_face_color}
    private final int[][] cubeFaces = {
            {3, 2, 1, 16}, {3, 1, 0, 16},
            {5, 6, 7, 32}, {4, 5, 7, 32},
            {1, 2, 6, 48}, {1, 6, 5, 48},
            {3, 0, 4, 64}, {3, 4, 7, 64},
            {0, 1, 5, 80}, {0, 5, 4, 80},
            {2, 3, 7, 96}, {2, 7, 6, 96}
        };
    
    private static final int SIN_TABLE_SIZE = 256;
    
    // pre-calculated 256 bytes lookup sin table (7 bit precision) [8 bit ? now i'm confused ...]
    private final int[] sinTable = {
        0, 3, 6, 9, 12, 15, 18, 21, 24, 28, 31, 34, 37, 40, 43, 46, 48, 51, 54, 
        57, 60, 63, 65, 68, 71, 73, 76, 78, 81, 83, 85, 88, 90, 92, 94, 96, 98, 
        100, 102, 104, 106, 108, 109, 111, 112, 114, 115, 117, 118, 119, 120, 
        121, 122, 123, 124, 124, 125, 126, 126, 127, 127, 127, 127, 127, 127, 
        127, 127, 127, 127, 127, 126, 126, 125, 124, 124, 123, 122, 121, 120, 
        119, 118, 117, 115, 114, 112, 111, 109, 108, 106, 104, 102, 100, 98, 
        96, 94, 92, 90, 88, 85, 83, 81, 78, 76, 73, 71, 68, 65, 63, 60, 57, 54, 
        51, 48, 46, 43, 40, 37, 34, 31, 28, 24, 21, 18, 15, 12, 9, 6, 3, 0, -3, 
        -6, -9, -12, -15, -18, -21, -24, -28, -31, -34, -37, -40, -43, -46, -48, 
        -51, -54, -57, -60, -63, -65, -68, -71, -73, -76, -78, -81, -83, -85, 
        -88, -90, -92, -94, -96, -98, -100, -102, -104, -106, -108, -109, -111, 
        -112, -114, -115, -117, -118, -119, -120, -121, -122, -123, -124, -124, 
        -125, -126, -126, -127, -127, -127, -127, -127, -128, -127, -127, -127, 
        -127, -127, -126, -126, -125, -124, -124, -123, -122, -121, -120, -119, 
        -118, -117, -115, -114, -112, -111, -109, -108, -106, -104, -102, -100, 
        -98, -96, -94, -92, -90, -88, -85, -83, -81, -78, -76, -73, -71, -68, 
        -65, -63, -60, -57, -54, -51, -48, -46, -43, -40, -37, -34, -31, -28, 
        -24, -21, -18, -15, -12, -9, -6, -3    
    };

    private int getSin(int a) {
        return sinTable[normalizeAngle(a)];
    }

    private int getCos(int a) {
        return sinTable[normalizeAngle(a - 64)]; // 64 in this case = 90 degrees
    }
    
    private int normalizeAngle(int a) {
        if (a > SIN_TABLE_SIZE - 1) { 
            a = a % SIN_TABLE_SIZE;
        }
        if (a < 0) {
            a += SIN_TABLE_SIZE;
        }
        return a;
    }
    
    // midi music
    private boolean midiInitialized = false;
    private Sequence sequence;
    private Sequencer sequencer;
    int drumIndex = 0;
    int drumIntensity = 0;
    
    // text banner
    private final String textMessage = 
        "                                 HELLO !!!      THIS IS ANOTHER QUICK "
        + "RETRO DEMO WRITTEN IN JAVA AND THE PURPUSE OF THIS TEST IS TO "
        + "TRY TO DO SOME 3D MATH WITHOUT USING FLOATING POINT NUMBERS.   "
        + "THAT'S RIGHT, IT USES ONLY INTEGERS (FIXED POINT MATH) :)   "
        + "IT ALSO USES A SMALL 256 BYTES LOOKUP SIN TABLE.   "
        + "THIS IS ALSO THE FIRST TIME I COULD DO SOMETHING SYNCHRONIZED "
        + "WITH BACKGROUND MUSIC :)   WELL, THAT'S IT.   THANKS FOR "
        + "WATCHING :) !!!";
    
    private Font textFont;
    private int textPositionX;    
    
    public View() {
        int sw = SCREEN_WIDTH;
        int sh = SCREEN_HEIGHT;
        frameBuffer = new BufferedImage(sw, sh + 50, 
                BufferedImage.TYPE_INT_ARGB);
        
        Raster raster = frameBuffer.getRaster();
        screen = ((DataBufferInt) raster.getDataBuffer()).getData();
    }
    
    public void start() {
        initilizeFont();
        initializeMidi();
        // main loop, something close to 30 fps
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                update();
                repaint();
            }
        }, 100, 1000 / 30);
    }

    private void initilizeFont() {
        try {
            InputStream is = getClass().getResourceAsStream("dq1.ttf");
            textFont = Font.createFont(Font.TRUETYPE_FONT, is);
            textFont = textFont.deriveFont(20.0f);
        } catch (FontFormatException | IOException ex) {
            Logger.getLogger(View.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(-1);
        }
    }
        
    private void initializeMidi() {
        try {
            InputStream is = this.getClass().getResourceAsStream("vk.mid");
            sequence = MidiSystem.getSequence(is);
            sequencer = MidiSystem.getSequencer();
            sequencer.addMetaEventListener((MetaMessage meta) -> {
                // 88 = start loop event ?
                if (meta.getType() == 88) {
                    drumIndex = 0;
                }
            });
            midiInitialized = true;
        }
        catch (IOException | InvalidMidiDataException | 
                MidiUnavailableException e) {
            
            System.out.println("could not initilize midi !");
            midiInitialized = false;
        }
        // start playback after 2 seconds
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    sequencer.setSequence(sequence);
                    sequencer.open();
                    sequencer.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
                    sequencer.start();        
                }
                catch (InvalidMidiDataException | MidiUnavailableException e) {
                    System.out.println("can't play midi music !");
                    midiInitialized = false;
                }
            }
        }, 2000);
    }
    
    private void update() {
        angle += 1;
        if (angle >= SIN_TABLE_SIZE) {
            angle -= SIN_TABLE_SIZE;
        }
        
        angle2 += 3;
        if (angle2 >= SIN_TABLE_SIZE) {
            angle2 -= SIN_TABLE_SIZE;
        }
                
        floorCameraX = 2000 + (getSin(angle) << 2);
        floorCameraZ = 2000 + (getCos(angle) << 3);    
        
        // drum music effect
        drumIntensity -= 20;
        if (drumIntensity < 1) drumIntensity = 1;
        if (midiInitialized) {
            synchronizeDrumEffect();
        }

        // text banner
        textPositionX -= 3;
        if (textPositionX < -10000) {
            textPositionX = 0;
        }
    }
    
    private void synchronizeDrumEffect() {
        if (sequencer == null) return;
        if (sequencer.getSequence() == null) return;
        
        // percussion track for "vk.mid"
        Track track = sequencer.getSequence().getTracks()[6];
        
        MidiEvent event = track.get(drumIndex);
        if (event.getTick() > sequencer.getTickPosition()) {
            return;
        }
            
        MidiMessage midiMessage = event.getMessage();
        int midiMsgStatus = midiMessage.getStatus();
        if ((midiMsgStatus & ShortMessage.NOTE_ON) == ShortMessage.NOTE_ON
            && (int) (midiMessage.getMessage()[1] & 0xff) == 40) {
                drumIntensity = 255;
        }        

        drumIndex++;
        if (drumIndex >= track.size()) {
            drumIndex = 0;
        }
    }
        
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g); 
        Graphics2D fbg = (Graphics2D) frameBuffer.getGraphics();
        drawOffscreen(fbg);
        g.drawImage(frameBuffer, 0, 0, 
            SCREEN_WIDTH * SCREEN_SCALE, SCREEN_HEIGHT * SCREEN_SCALE, 
            0, 0, SCREEN_WIDTH, SCREEN_HEIGHT, null);
    }
    
    private void drawOffscreen(Graphics2D g) {
        g.setBackground(Color.BLACK);
        g.clearRect(0, 0, getWidth(), getHeight());
        drawFloor();
        drawCube();
        drawWaveText(g);
    }

    private void drawFloor() {
        for (int screenY = 0; screenY < 90; screenY++) {
            int z = (floorCameraY * d) / (floorCameraY - screenY);
            for (int screenX = 0; screenX < 320; screenX++) {
                int x = ((screenX - 160) * z) / d; 
                int tx = x + floorCameraX;
                int tz = z + floorCameraZ;
                int testX = (tx >> 7) & 1;
                int testZ = (tz >> 7) & 1;
                int color = (testX == 0 && testZ == 1) 
                    || (testX == 1 && testZ == 0) ? 255 : 0;
                color = color | 0xff000000;
                screen[screenX + (199 - screenY) * SCREEN_WIDTH] = color;
            }
        }
    }
    
    private void drawCube() {
        for (int i = 0; i < cubeLocalPoints.length; i++) {
            int[] point = cubeLocalPoints[i];
            int x = point[0];
            int y = point[1];
            int z = point[2];
            int s1 = getSin(angle);
            int c1 = getCos(angle);
            int s2 = getSin(angle2);
            int c2 = getCos(angle2);
            // rotate y
            int nx = (x * c2 - z * s2) >> 7;
            int nz = (x * s2 + z * c2) >> 7;
            x = nx;
            z = nz;
            // rotate x
            nz = (z * c1 - y * s1) >> 7;
            int ny = (z * s1 + y * c1) >> 7;
            z = nz;
            y = ny;
            // translate
            z = z - 1200 + (c1 << 1);
            x = x + (s2 << 1);
            y = y - 32 - (drumIntensity >> 1) + (s1 >> 2);
            // cube perspective transformation
            int cubeScreenX = d * x / -z; 
            int cubeScreenY = d * y / -z; 
            // cube perspective transformation
            int cubeShadowScreenX = d * x / -z; 
            int cubeShadowScreenY = d * (200) / -z; 
            // screen space adjust
            cubeScreenX += 160;
            cubeScreenY += 100;
            cubeShadowScreenX += 160;
            cubeShadowScreenY += 100;
            
            cubeScreenPoints[i][0] =  cubeScreenX;
            cubeScreenPoints[i][1] =  cubeScreenY;
            cubeShadowScreenPoints[i][0] = cubeShadowScreenX;
            cubeShadowScreenPoints[i][1] = cubeShadowScreenY;
        }
        // draw cube shadow
        for (int[] triangle : cubeFaces) {
            int px1 = cubeShadowScreenPoints[triangle[0]][0];
            int py1 = cubeShadowScreenPoints[triangle[0]][1];
            int px2 = cubeShadowScreenPoints[triangle[1]][0];
            int py2 = cubeShadowScreenPoints[triangle[1]][1];
            int px3 = cubeShadowScreenPoints[triangle[2]][0];
            int py3 = cubeShadowScreenPoints[triangle[2]][1];
            fillTriangle(px1, py1, px2, py2, px3, py3, 0xff000000);
        }
        // draw cube
        for (int[] triangle : cubeFaces) {
            int px1 = cubeScreenPoints[triangle[0]][0];
            int py1 = cubeScreenPoints[triangle[0]][1];
            int px2 = cubeScreenPoints[triangle[1]][0];
            int py2 = cubeScreenPoints[triangle[1]][1];
            int px3 = cubeScreenPoints[triangle[2]][0];
            int py3 = cubeScreenPoints[triangle[2]][1];
            // back face culling
            int vx1 = (px1 - px2);
            int vy1 = (py1 - py2);
            int vx2 = (px3 - px2);
            int vy2 = (py3 - py2);
            if (vx1 * vy2 >= vy1 * vx2) {
                continue;
            }
            // drum music color effect
            int minFaceColor = triangle[3];
            int faceColor = (drumIntensity < minFaceColor) 
                    ? minFaceColor : drumIntensity;
            
            faceColor = (faceColor << 16) + (faceColor << 8) + faceColor;
            fillTriangle(px1, py1, px2, py2, px3, py3, 0xff000000 | faceColor);
        }
    }
    
    // this fill triangle function is not as beautiful 
    // as bresenham's algorithm but at least it works xD ...
    public void fillTriangle(int px1, int py1, int px2, int py2, int px3, 
            int py3, int color) {
        
        int[] ordered = new int[6];
        if (!orderByPointY(px1, py1, px2, py2, px3, py3, ordered)) {
            if (!orderByPointY(px2, py2, px1, py1, px3, py3, ordered)) {
                if (!orderByPointY(px3, py3, px2, py2, px1, py1, ordered)) {
                    String errorMsg = "could not order triangle points !";
                    throw new RuntimeException(errorMsg);
                }
            }
        }
        px1 = ordered[0]; py1 = ordered[1];
        px2 = ordered[2]; py2 = ordered[3];
        px3 = ordered[4]; py3 = ordered[5];
        fillHalfTriangle(px1, py1, px2, py2, px3, py3, color, 1); // top
        fillHalfTriangle(px3, py3, px2, py2, px1, py1, color, -1); // down
    }

    private void fillHalfTriangle(int px1, int py1, int px2, int py2,
            int px3, int py3, int color, int sign) {
        
        int a, b, ma, mb;
        int x1 = 0, x2 = 0, resta = 0, restb = 0, p1 = px1, p2 = px1;
        boolean lastLine = false;
        for (int y = py1; sign > 0 ? y < py2 : y > py2; y += sign) {
            a = ((px2 - px1) + resta);
            b = (py2 - py1);
            ma = a / b;
            resta = a % b;
            a = ((px3 - px1) + restb);
            b = (py3 - py1);
            mb = a / b;
            restb = a % b;
            p1 += sign * ma;
            p2 += sign * mb;
            x1 = p1 <= p2 ? p1 : p2;
            x2 = p1 > p2 ? p1 : p2;
            for (int x = x1; x <= x2; x++) {
                screen[x + y * SCREEN_WIDTH] = color;
            }
            lastLine = true;
        }
        if (lastLine) {
            for (int x = x1; x <= x2; x++) {
                screen[x + py2 * SCREEN_WIDTH] = color;
            }
        }
    }
    
    private boolean orderByPointY(int px1, int py1, int px2, int py2, 
            int px3, int py3, int[] orderedY) {
        
        if (py1 <= py2 && py1 <= py3) {
            orderedY[0] = px1;
            orderedY[1] = py1;
            if (py2 <= py3) {
                orderedY[2] = px2;
                orderedY[3] = py2;
                orderedY[4] = px3;
                orderedY[5] = py3;
            }
            else {
                orderedY[2] = px3;
                orderedY[3] = py3;
                orderedY[4] = px2;
                orderedY[5] = py2;
            }
            return true;
        }
        return false;
    }

    private void drawWaveText(Graphics2D g) {
        g.setBackground(new Color(255, 255, 255, 0));
        g.clearRect(0, 200, 320, 250);
        g.setFont(textFont);
        g.setColor(Color.BLACK);
        for (int y=-2; y<=2; y++) {
            for (int x=-2; x<=2; x++) {
                g.drawString(textMessage, x + textPositionX, y + 240);
            }
        }
        g.setColor(Color.WHITE);
        g.drawString(textMessage, textPositionX, 240);
        for (int x = 0; x < getWidth(); x++) {
            int y = (-20 + ((15 * getSin((angle << 3) + x)) >> 7));
            y += (130 + (10 * getCos(angle2 << 2) >> 7));
            g.drawImage(frameBuffer, x, y, x + 1, y + 50, 
                x, 200, x + 1, 250, null);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            View view = new View();
            view.setPreferredSize(new Dimension(SCREEN_WIDTH * SCREEN_SCALE, 
                    SCREEN_HEIGHT * SCREEN_SCALE));
            
            JFrame frame = new JFrame();
            frame.setTitle("Java 3D Demo using Fixed-Point Math Test");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.getContentPane().add(view);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            view.start();
        });
    }
    
}
