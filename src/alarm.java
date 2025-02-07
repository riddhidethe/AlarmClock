
import java.awt.Frame;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/*km,
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
public class alarm extends javax.swing.JFrame implements Runnable {
    // Logger for debugging and logging errors
    private static final Logger LOGGER = Logger.getLogger(alarm.class.getName());
    private Clip clip;
    
    private File defaultSoundFile;
    private File selectedSoundFile;
    
    private Timer snoozeTimer;
    
    private boolean alarmTriggered = false;
    private boolean isAlarmRinging = false;
    
    private String notify = "";
    
    private DefaultListModel<String> listModel; // Model for managing alarm list
    private List<Clip> activeClips = new ArrayList<>(); // List of active audio clips for managing multiple alarms
    
    // Variables for managing time
    String hr, h, m;
    String hralarm, minalarm;
    /**
     * Creates new form alarm
     */
    public alarm() {
        initComponents();
        // Start a thread for real-time clock updates
        Thread t = new Thread(this);
        listModel = new DefaultListModel<>();
        t.start();
        
        // Set current hour and minute
        Calendar c = Calendar.getInstance();
        SimpleDateFormat sdf1 = new SimpleDateFormat("hh");
        SimpleDateFormat sdf2 = new SimpleDateFormat("mm");
        Date date = new Date();
        h = sdf1.format(date);
        m = sdf2.format(date);
        updateAlarmListUI(); // Refresh the UI with alarms from the database
        
        // Set default hour and minute in dropdowns
        jComboBox1.setSelectedItem(h);
        jComboBox2.setSelectedItem(m);
        jButton3.setEnabled(false); // Disable the "Listen" button initially
        
        // Set default alarm sound file
        defaultSoundFile = new File("C:\\Users\\91937\\Documents\\NetBeansProjects\\Alarm clock UI\\src\\Playlists\\Ringtone1.wav"); 
        if (!defaultSoundFile.exists()) {
            JOptionPane.showMessageDialog(this, "Default sound file not found.");
        }
        selectedSoundFile = defaultSoundFile;
        jLabel5.setText("Alarm Music : "+selectedSoundFile.getName()); // Display the name of the selected sound file
    }
    
    // Method to allow users to select a custom alarm sound file.
    public void chooseSong() {
        JFileChooser jfc = new JFileChooser(); // File chooser dialog
        jfc.setDialogTitle("Select Sound File");
        jfc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Sound Files (.wav)", "wav"));

        int result = jfc.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File chosenFile = jfc.getSelectedFile();
            if (chosenFile.getName().toLowerCase().endsWith(".wav")) {
                selectedSoundFile = chosenFile; // Set the selected file
                JOptionPane.showMessageDialog(this, "Selected sound file: " + selectedSoundFile.getName());
            } else {
                JOptionPane.showMessageDialog(this, "Invalid file type. Please select a .wav file.");
            }
        }
    }
    
    // Method to set a new alarm.
    public void setAlarm() {
        try {
            SwingUtilities.invokeLater(() -> jButton4.setEnabled(true)); // Enable snooze button
            notify = jTextField1.getText(); // Get notification message

            // Default message if no custom message is provided
            if (notify.equals("Enter Notification") || notify.isBlank()) {
                notify = "Time to wake up!";
            }

            // Parse hour and minute from dropdowns
            int hr = Integer.parseInt(jComboBox1.getSelectedItem().toString());
            int min = Integer.parseInt(jComboBox2.getSelectedItem().toString());
            String ampm = jComboBox3.getSelectedItem().toString();
            
            // Convert to 24-hour format if necessary
            if(ampm.equals("PM") && hr != 12) {
                hr += 12;
            }
            else if(ampm.equals("AM") && hr == 12) {
                hr = 0;
            }
            String alarmTime = String.format("%02d:%02d", hr, min);
            // Ensure a sound file is selected
            if (selectedSoundFile == null) {
                JOptionPane.showMessageDialog(null, "Please select a sound file for the alarm.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // Check if Repeat is selected
            boolean isRepeat = jRadioButton1.isSelected();
            
            // Save the alarm in the database
            try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/alarmclock", "root", "RJAH#1234@md");
                Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("INSERT INTO alarms(alarm_time, sound_file_path, msg, is_repeat) VALUES ('" + alarmTime + "', '" + selectedSoundFile + "', '" + notify + "', " + isRepeat + ")");
            }
            listModel.addElement(alarmTime); // Add to the UI list
            updateAlarmListUI(); // Refresh the UI
            checkAlarm(); // Start monitoring for alarms
            JOptionPane.showMessageDialog(null, "Alarm set for: " + alarmTime);
        }
        catch(Exception ex){
            LOGGER.log(Level.SEVERE, "Failed to set alarm", ex);
            showError("Error setting alarm: " + ex.getMessage());
        }
    }
    
    // Method to monitor alarms
    private void checkAlarm() {
        Timer triggerCheckTimer = new Timer(true);
        triggerCheckTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/alarmclock", "root", "RJAH#1234@md");
                     Statement statement = connection.createStatement();
                     ResultSet rs = statement.executeQuery("SELECT * FROM alarms")) {

                    while (rs.next()) {
                        String alarmTime = rs.getString("alarm_time");
                        boolean isRepeat = rs.getBoolean("is_repeat");
                        boolean isTriggered = rs.getBoolean("is_triggered");

                        // Parse the alarm time
                        LocalTime alarmLocalTime = LocalTime.parse(alarmTime, DateTimeFormatter.ofPattern("HH:mm:ss"));
                        Duration timeDifference = Duration.between(LocalTime.now(), alarmLocalTime);

                        // Check if it's time to trigger the alarm
                        if (timeDifference.abs().getSeconds() <= 0) {
                            startAlarm(selectedSoundFile); // Play the alarm sound

                            if (!isRepeat) {
                                // Mark as triggered for non-repeating alarms
                                try (PreparedStatement updateStmt = connection.prepareStatement(
                                        "UPDATE alarms SET is_triggered = true WHERE alarm_time = ? AND is_repeat = false")) {
                                    updateStmt.setString(1, alarmTime);
                                    updateStmt.executeUpdate();
                                }
                            }
                        }

                        // For non-repeating alarms, ensure they trigger only once
                        if (!isRepeat && isTriggered) {
                            continue; // Skip already triggered alarms
                        }
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "Error checking alarms", ex);
                }
            }
        }, 0, 1000); // Check every second
    }
    
    // Method to start the alarm at set time
    public void startAlarm(File soundFile) {
        try {
            // Stop and close the previous clip if it's still open
        if (clip != null && clip.isOpen()) {
            clip.stop();
            clip.close();
        }
        if (soundFile.exists()) {
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundFile);
            clip = AudioSystem.getClip();
            clip.open(audioInputStream);
            clip.start();
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            activeClips.add(clip);
            isAlarmRinging = true;
            
            // Bring the app to front if minimized
            SwingUtilities.invokeLater(() -> {
            Frame[] frames = Frame.getFrames();
                for (Frame frame : frames) {
                    if (frame.isVisible()) {
                        frame.setState(Frame.NORMAL); // Restore if minimized
                        frame.toFront();             // Bring to front
                        frame.requestFocus();        // Request focus
                    }
                }
            });
            JOptionPane.showMessageDialog(null, notify);
        } else {
            JOptionPane.showMessageDialog(this, "Sound file not found "+ soundFile);
        }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to play alarm sound", ex);
            showError("Error playing alarm sound: " + ex.getMessage());
        }
    }
    
    // Method to update the alarm list
    private void updateAlarmListUI() {
        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/alarmclock", "root", "RJAH#1234@md");
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("select * from alarms")) {
            
            // Clear the existing list
            listModel.clear();

            // Populate the list with alarms from the database
            while (rs.next()) {
                String alarmTime = rs.getString("alarm_time");
                String msg = rs.getString("msg");
                listModel.addElement(alarmTime + " - " + msg); // Add alarm details to the list
            }
            jList1.setModel(listModel);
        } catch(Exception ex){
            LOGGER.log(Level.SEVERE, "Failed to load alarms", ex);
        }
    }
    
    // Method to snooze the alarm
    public void snoozeAlarm(int min) {
        if (isAlarmRinging) {
            stopAlarm();
            snoozeTimer = new Timer();
            snoozeTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    startAlarm(selectedSoundFile);
                    jButton4.setEnabled(true);
                }
            }, min * 60 * 1000); // Convert minutes to milliseconds
        JOptionPane.showMessageDialog(this, "Alarm snoozed for " + min + " minutes.");
        }
    }
    
    // Method to stop the ringing alarm
    public void stopAlarm() {
        for (Clip clip : activeClips) {
            if (clip.isRunning()) {
                clip.stop();
                clip.close();
                System.out.println("Audio clip successfully stopped.");
            }else {
                System.out.println("Audio clip was not running.");
            }
        }
        activeClips.clear(); // Clear the list of active clips
        isAlarmRinging = false;
        SwingUtilities.invokeLater(() -> jButton4.setEnabled(false)); // Disable the "Snooze" button
    }

    // To display the real time and date 
    @Override
    public void run() {
        while (true) {
            try {
                SwingUtilities.invokeLater(() -> {
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                    Date time = new Date();
                    jLabel1.setText(sdf.format(time));
                    SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd");
                    Date date = new Date();
                    jLabel2.setText(sdf1.format(date));
                });
                Thread.sleep(1000); // Update every second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                showError("Error: " + e.getMessage());
                break;
            }
        }
    }
    
    // Method to show the errors
    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup1 = new javax.swing.ButtonGroup();
        jPanel1 = new javax.swing.JPanel();
        jLabel4 = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        jList1 = new javax.swing.JList<>();
        jComboBox1 = new javax.swing.JComboBox<>();
        jComboBox2 = new javax.swing.JComboBox<>();
        jComboBox3 = new javax.swing.JComboBox<>();
        jLabel3 = new javax.swing.JLabel();
        jButton1 = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();
        jButton3 = new javax.swing.JButton();
        jLabel5 = new javax.swing.JLabel();
        jTextField1 = new javax.swing.JTextField();
        jRadioButton1 = new javax.swing.JRadioButton();
        jRadioButton2 = new javax.swing.JRadioButton();
        jButton4 = new javax.swing.JButton();
        jButton5 = new javax.swing.JButton();
        jButton6 = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Alarm Clock");

        jPanel1.setBackground(new java.awt.Color(204, 255, 255));

        jLabel4.setBackground(new java.awt.Color(255, 255, 255));
        jLabel4.setIcon(new javax.swing.ImageIcon("C:\\Users\\91937\\Documents\\clock.jpg")); // NOI18N
        jLabel4.setText("jLabel4");

        jLabel1.setBackground(new java.awt.Color(204, 204, 204));
        jLabel1.setFont(new java.awt.Font("BankGothic Md BT", 1, 24)); // NOI18N
        jLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);

        jLabel2.setBackground(new java.awt.Color(204, 204, 204));
        jLabel2.setFont(new java.awt.Font("BankGothic Md BT", 1, 18)); // NOI18N
        jLabel2.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);

        jList1.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        jScrollPane1.setViewportView(jList1);

        jComboBox1.setFont(new java.awt.Font("SansSerif", 0, 14)); // NOI18N
        jComboBox1.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "00", "01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12" }));
        jComboBox1.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        jComboBox1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jComboBox1ActionPerformed(evt);
            }
        });

        jComboBox2.setFont(new java.awt.Font("SansSerif", 0, 14)); // NOI18N
        jComboBox2.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "00", "01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31", "32", "33", "34", "35", "36", "37", "38", "39", "40", "41", "42", "43", "44", "45", "46", "47", "48", "49", "50", "51", "52", "53", "54", "55", "56", "57", "58", "59" }));
        jComboBox2.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        jComboBox3.setFont(new java.awt.Font("SansSerif", 1, 14)); // NOI18N
        jComboBox3.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "AM", "PM" }));
        jComboBox3.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        jLabel3.setFont(new java.awt.Font("SansSerif", 1, 18)); // NOI18N
        jLabel3.setText(":");

        jButton1.setBackground(new java.awt.Color(204, 204, 204));
        jButton1.setFont(new java.awt.Font("SansSerif", 1, 18)); // NOI18N
        jButton1.setText("Set Alarm");
        jButton1.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        jButton2.setBackground(new java.awt.Color(204, 204, 204));
        jButton2.setFont(new java.awt.Font("SansSerif", 1, 18)); // NOI18N
        jButton2.setText("Ringtone");
        jButton2.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });

        jButton3.setBackground(new java.awt.Color(204, 204, 204));
        jButton3.setFont(new java.awt.Font("SansSerif", 1, 18)); // NOI18N
        jButton3.setText("Listen");
        jButton3.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        jButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton3ActionPerformed(evt);
            }
        });

        jLabel5.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);

        jTextField1.setFont(new java.awt.Font("SansSerif", 0, 14)); // NOI18N
        jTextField1.setText("Enter Notification");

        buttonGroup1.add(jRadioButton1);
        jRadioButton1.setFont(new java.awt.Font("SansSerif", 0, 14)); // NOI18N
        jRadioButton1.setText("Repeat");
        jRadioButton1.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        jRadioButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButton1ActionPerformed(evt);
            }
        });

        buttonGroup1.add(jRadioButton2);
        jRadioButton2.setFont(new java.awt.Font("SansSerif", 0, 14)); // NOI18N
        jRadioButton2.setSelected(true);
        jRadioButton2.setText("Once");
        jRadioButton2.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        jRadioButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButton2ActionPerformed(evt);
            }
        });

        jButton4.setBackground(new java.awt.Color(204, 204, 204));
        jButton4.setFont(new java.awt.Font("SansSerif", 1, 18)); // NOI18N
        jButton4.setText("Snooze");
        jButton4.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        jButton4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton4ActionPerformed(evt);
            }
        });

        jButton5.setBackground(new java.awt.Color(204, 204, 204));
        jButton5.setFont(new java.awt.Font("SansSerif", 1, 18)); // NOI18N
        jButton5.setText("Dismiss");
        jButton5.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        jButton5.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton5ActionPerformed(evt);
            }
        });

        jButton6.setBackground(new java.awt.Color(204, 204, 204));
        jButton6.setFont(new java.awt.Font("SansSerif", 1, 18)); // NOI18N
        jButton6.setText("Delete Alarm");
        jButton6.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        jButton6.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton6ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 196, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, 196, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(29, 29, 29))
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGap(6, 6, 6)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabel4, javax.swing.GroupLayout.PREFERRED_SIZE, 124, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addGroup(jPanel1Layout.createSequentialGroup()
                                        .addGap(26, 26, 26)
                                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addGroup(jPanel1Layout.createSequentialGroup()
                                                .addComponent(jComboBox1, javax.swing.GroupLayout.PREFERRED_SIZE, 54, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(jLabel3)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(jComboBox2, javax.swing.GroupLayout.PREFERRED_SIZE, 54, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                                .addComponent(jComboBox3, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE))
                                            .addGroup(jPanel1Layout.createSequentialGroup()
                                                .addGap(6, 6, 6)
                                                .addComponent(jRadioButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 98, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                                .addComponent(jRadioButton2, javax.swing.GroupLayout.PREFERRED_SIZE, 85, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                                .addGap(0, 27, Short.MAX_VALUE))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                                .addGap(0, 0, Short.MAX_VALUE)
                                .addComponent(jButton2, javax.swing.GroupLayout.PREFERRED_SIZE, 122, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(jButton3, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE))))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(jButton4, javax.swing.GroupLayout.PREFERRED_SIZE, 121, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jButton5, javax.swing.GroupLayout.PREFERRED_SIZE, 121, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(18, 18, 18)
                .addComponent(jButton6)
                .addGap(14, 14, 14))
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(67, 67, 67)
                        .addComponent(jButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 141, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel5, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 254, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jTextField1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 254, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)))
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 144, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(14, 14, 14))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(18, 18, 18)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jLabel4))
                .addGap(16, 16, 16)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 202, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jComboBox2, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel3)
                            .addComponent(jComboBox3, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jComboBox1, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jRadioButton1)
                            .addComponent(jRadioButton2, javax.swing.GroupLayout.PREFERRED_SIZE, 24, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(12, 12, 12)
                        .addComponent(jButton1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jButton3)
                            .addComponent(jButton2))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel5, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jButton4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jButton5)
                            .addComponent(jButton6))
                        .addGap(16, 16, 16))))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        );

        pack();
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        // TODO add your handling code here:
        setAlarm();
    }//GEN-LAST:event_jButton1ActionPerformed

    private void jComboBox1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBox1ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_jComboBox1ActionPerformed

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
        // TODO add your handling code here:
        chooseSong();
        if (selectedSoundFile != null) {
            jLabel5.setText("Alarm Music : "+selectedSoundFile.getName());
        }
        jButton3.setEnabled(true); // Enable listen button
    }//GEN-LAST:event_jButton2ActionPerformed

    private void jButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton3ActionPerformed
        // TODO add your handling code here:
        if(jButton3.getText().equals("Listen")) {
            try {
                AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(selectedSoundFile);
                clip = AudioSystem.getClip();
                clip.open(audioInputStream);
                clip.start();
            }catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Failed to play alarm sound", ex);
                showError("Error playing alarm sound: " + ex.getMessage());
            }
            jButton3.setText("Stop Sound");
            jButton1.setEnabled(false);
        }
        else if (jButton3.getText().equals("Stop Sound")) {
            stopAlarm();
            jButton3.setText("Listen");
            jButton1.setEnabled(true);
        }
    }//GEN-LAST:event_jButton3ActionPerformed

    private void jButton4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton4ActionPerformed
        // TODO add your handling code here:
        jButton4.setEnabled(false);
        if (isAlarmRinging) { // Check if the alarm is ringing
            snoozeAlarm(1); // Snooze for the specified interval 
        } else {
            JOptionPane.showMessageDialog(null, "The alarm hasn't rung yet.");
        }
    }//GEN-LAST:event_jButton4ActionPerformed

    private void jButton6ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton6ActionPerformed
        // TODO add your handling code here:
        String selectedAlarm = jList1.getSelectedValue();

        if (selectedAlarm == null) {
            JOptionPane.showMessageDialog(null, "Please select an alarm to delete.");
            return;
        }
        // Extract alarm time and message
        String[] alarmDetails = selectedAlarm.split(" - ", 2);
        if (alarmDetails.length < 2) {
            JOptionPane.showMessageDialog(null, "Invalid alarm format.");
            return;
        }
        String alarmTime = alarmDetails[0];
        String msg = alarmDetails[1];

        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/alarmclock", "root", "RJAH#1234@md");
            PreparedStatement pstmt = conn.prepareStatement("DELETE FROM alarms WHERE alarm_time = ? AND msg = ?")) {
           
            pstmt.setString(1, alarmTime);
            pstmt.setString(2, msg);

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                JOptionPane.showMessageDialog(null, "Alarm deleted successfully.");
                LOGGER.log(Level.INFO, "Alarm deleted: Time = {0}, Message = {1}", new Object[]{alarmTime, msg});
                updateAlarmListUI(); // Refresh the alarm list
            } else {
                JOptionPane.showMessageDialog(null, "Failed to delete alarm.");
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Database error occurred while deleting alarm", ex);
            showError("Error deleting alarm: " + ex.getMessage());
        } 
    }//GEN-LAST:event_jButton6ActionPerformed

    private void jButton5ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton5ActionPerformed
        // TODO add your handling code here:
        System.out.println("Stop button clicked");
        stopAlarm();
        JOptionPane.showMessageDialog(this, "Alarm stopped.");
    }//GEN-LAST:event_jButton5ActionPerformed

    private void jRadioButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButton2ActionPerformed
        // TODO add your handling code here:
        if (jRadioButton2.isSelected()) {
            jRadioButton1.setSelected(false); // Ensure "Repeat" is deselected
        }
    }//GEN-LAST:event_jRadioButton2ActionPerformed

    private void jRadioButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButton1ActionPerformed
        // TODO add your handling code here:
        if (jRadioButton1.isSelected()) {
            jRadioButton2.setSelected(false); // Ensure "Once" is deselected
        }
    }//GEN-LAST:event_jRadioButton1ActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(alarm.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(alarm.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(alarm.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(alarm.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new alarm().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton3;
    private javax.swing.JButton jButton4;
    private javax.swing.JButton jButton5;
    private javax.swing.JButton jButton6;
    private javax.swing.JComboBox<String> jComboBox1;
    private javax.swing.JComboBox<String> jComboBox2;
    private javax.swing.JComboBox<String> jComboBox3;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JList<String> jList1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JRadioButton jRadioButton1;
    private javax.swing.JRadioButton jRadioButton2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTextField jTextField1;
    // End of variables declaration//GEN-END:variables
}
