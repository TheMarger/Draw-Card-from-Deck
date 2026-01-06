// gamePanel.java
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.awt.geom.AffineTransform;

/**
 * gamePanel - updated so the remaining-cards list appears ONLY on the left panel.
 */
public class gamePanel extends JPanel {

    // UI states
    private enum State { SETUP, PLAY, RESULT }
    private State currentState = State.SETUP;

    // Card/type model
    private enum Suit { HEARTS("♥"), DIAMONDS("♦"), CLUBS("♣"), SPADES("♠");
        final String glyph; Suit(String g){ glyph = g; }
        public String glyph(){ return glyph; }
    }
    private enum ColorType { RED, BLACK }
    private static final String[] RANKS = {"A","2","3","4","5","6","7","8","9","10","J","Q","K"};

    private static class Card {
        final String rank;
        final Suit suit;
        Card(String rank, Suit suit){ this.rank = rank; this.suit = suit; }
        boolean isFace(){ return "J".equals(rank) || "Q".equals(rank) || "K".equals(rank); }
        ColorType color(){ return (suit == Suit.HEARTS || suit == Suit.DIAMONDS) ? ColorType.RED : ColorType.BLACK; }
        @Override public String toString(){ return rank + suit.glyph(); }
    }

    // Helper to create multiplier input fields (class-level method)
    private JTextField makeMulField(double value) {
        JTextField f = new JTextField(String.valueOf(value));
        f.setForeground(Color.WHITE);
        f.setBackground(new Color(55,60,70));
        f.setCaretColor(Color.WHITE);
        f.setBorder(BorderFactory.createEmptyBorder(4,6,4,6));
        f.setPreferredSize(new Dimension(90,26));
        return f;
    }

    private static class Deck {
        private final List<Card> cards = new ArrayList<>();
        Deck(){ resetToFull(); }
        void resetToFull(){
            cards.clear();
            for (Suit s : Suit.values()){
                for (String r : RANKS) cards.add(new Card(r,s));
            }
        }
        void clear(){ cards.clear(); }
        int size(){ return cards.size(); }
        List<Card> asList(){ return Collections.unmodifiableList(cards); }
        void removeSuit(Suit suit){ cards.removeIf(c -> c.suit == suit); }
        void addSuit(Suit suit){
            for (String r : RANKS){
                Card c = new Card(r,suit);
                boolean exists = false;
                for (Card cc : cards) if (cc.rank.equals(c.rank) && cc.suit == c.suit) { exists = true; break; }
                if (!exists) cards.add(c);
            }
        }
        void removeColor(ColorType color){ cards.removeIf(c -> c.color() == color); }
        void addColor(ColorType color){
            for (Suit s : Suit.values()){
                if ((color == ColorType.RED && (s==Suit.HEARTS || s==Suit.DIAMONDS)) ||
                    (color == ColorType.BLACK && (s==Suit.CLUBS || s==Suit.SPADES))){
                    addSuit(s);
                }
            }
        }
        void removeFaces(){ cards.removeIf(Card::isFace); }
        void addFaces(){
            for (Suit s : Suit.values()){
                for (String r : new String[]{"J","Q","K"}){
                    boolean exists = false;
                    for (Card c : cards) if (c.rank.equals(r) && c.suit == s) { exists = true; break; }
                    if (!exists) cards.add(new Card(r,s));
                }
            }
        }
        Card drawRandom(Random rng){
            if (cards.isEmpty()) return null;
            int idx = rng.nextInt(cards.size());
            return cards.remove(idx);
        }
        boolean removeCard(String rank, Suit suit){
            return cards.removeIf(c -> c.rank.equals(rank) && c.suit == suit);
        }
        boolean contains(String rank, Suit suit){
            for (Card c : cards) if (c.rank.equals(rank) && c.suit == suit) return true;
            return false;
        }
        // add single card safely
        void addCard(String rank, Suit suit){
            boolean exists = false;
            for (Card c : cards) if (c.rank.equals(rank) && c.suit == suit) { exists = true; break; }
            if (!exists) cards.add(new Card(rank, suit));
        }
        // shuffle
        void shuffle(Random rng){
            Collections.shuffle(cards, rng);
        }
    }

    // Chosen bet & type
    private enum ChosenType { INDIVIDUAL, SUIT, COLOUR, FACE }
    private int betAmount = 0;
    private ChosenType chosenType = ChosenType.INDIVIDUAL;
    private String chosenRank = "A";
    private Suit chosenSuit = Suit.SPADES;
    private ColorType chosenColor = ColorType.RED;
    private final Deck deck = new Deck();
    private final Random rng = new Random();
    private Card lastDrawn = null;

    // Multipliers (editable in settings)
    private double mulIndividual = 4.0;
    private double mulSuit = 3.0;
    private double mulColour = 2.0;
    private double mulFace = 2.0;

    // Swing components
    private final CardComponent cardComponent = new CardComponent();
    private JLabel deckCountLabel = new JLabel();
    private JButton drawButton = new JButton("Draw");
    private JLabel topInfoLabel = new JLabel();
    private JPanel centerPanel = new JPanel(new BorderLayout());

    // Left-side remaining-cards list model & UI
    private DefaultListModel<String> deckListModel = new DefaultListModel<>();
    private JList<String> deckList = new JList<>(deckListModel);

    // Theme
    private Color panelBg = new Color(28,34,40);
    private Color accent = new Color(45,160,200);

    public gamePanel() {
        setLayout(new BorderLayout());
        setBackground(panelBg);
        setPreferredSize(new Dimension(1200, 820));
        updateGlobalFont(new Font("Segoe UI", Font.PLAIN, 14));
        setupGame();
    }

    private void updateGlobalFont(Font f){
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while(keys.hasMoreElements()){
            Object k = keys.nextElement();
            Object v = UIManager.get(k);
            if (v instanceof Font) UIManager.put(k, f);
        }
    }

    /* ---------------------- Setup screen UI ---------------------- */
    public void setupGame(){
        removeAll();
        deck.resetToFull();
        lastDrawn = null;
        currentState = State.SETUP;
        setUpSetupScreen();
        revalidate();
        repaint();
    }

    private void setUpSetupScreen(){
        this.removeAll();

        // Top toolbar (no balance)
        JPanel topBar = createTopBar();
        add(topBar, BorderLayout.NORTH);

        // Center: single clean setup card (no preview, no multipliers)
        JPanel main = new JPanel(new GridBagLayout());
        main.setOpaque(false);

        JPanel card = new RoundedPanel(new Color(40,46,54), 16);
        card.setLayout(new BorderLayout(12,12));
        card.setBorder(new EmptyBorder(18,18,18,18));
        card.setPreferredSize(new Dimension(760, 360));

        JLabel title = new JLabel("Place your bet");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 26f));
        title.setForeground(Color.WHITE);
        card.add(title, BorderLayout.NORTH);

        JPanel inputs = new JPanel(new GridBagLayout());
        inputs.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8,10,8,10);
        c.anchor = GridBagConstraints.WEST;

        // Bet
        c.gridx = 0; c.gridy = 0;
        JLabel betLbl = new JLabel("Bet (integer): ");
        betLbl.setForeground(Color.WHITE);
        inputs.add(betLbl, c);
        c.gridx = 1;
        JTextField betField = stylizeField(new JTextField("10",10));
        inputs.add(betField, c);

        // Type
        c.gridx = 0; c.gridy = 1;
        JLabel pickLbl = new JLabel("Pick type: ");
        pickLbl.setForeground(Color.WHITE);
        inputs.add(pickLbl, c);
        c.gridx = 1;
        String[] typeOptions = {"Individual card", "Suit", "Colour", "Face"};
        JComboBox<String> typeBox = stylizeCombo(new JComboBox<>(typeOptions));
        inputs.add(typeBox, c);

        // Subchoice area
        c.gridx = 0; c.gridy = 2; c.gridwidth = 2;
        JPanel subChoicePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        subChoicePanel.setOpaque(false);
        inputs.add(subChoicePanel, c);

        JComboBox<String> rankBox = stylizeCombo(new JComboBox<>(RANKS));
        JComboBox<String> suitBox = stylizeCombo(new JComboBox<>(new String[]{"HEARTS","DIAMONDS","CLUBS","SPADES"}));
        subChoicePanel.add(new JLabel("Rank:")); subChoicePanel.add(rankBox);
        subChoicePanel.add(new JLabel("Suit:")); subChoicePanel.add(suitBox);

        JComboBox<String> colorBox = stylizeCombo(new JComboBox<>(new String[]{"RED","BLACK"}));
        JPanel faceInfo = new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));
        faceInfo.setOpaque(false);
        JLabel faceLabel = new JLabel("Face = J, Q, K");
        faceLabel.setForeground(Color.LIGHT_GRAY);
        faceInfo.add(faceLabel);

        typeBox.addActionListener(e -> {
            String sel = (String) typeBox.getSelectedItem();
            subChoicePanel.removeAll();
            if ("Individual card".equals(sel)){
                subChoicePanel.add(new JLabel("Rank:")); subChoicePanel.add(rankBox);
                subChoicePanel.add(new JLabel("Suit:")); subChoicePanel.add(suitBox);
            } else if ("Suit".equals(sel)){
                subChoicePanel.add(new JLabel("Suit:")); subChoicePanel.add(suitBox);
            } else if ("Colour".equals(sel)){
                subChoicePanel.add(new JLabel("Colour:")); subChoicePanel.add(colorBox);
            } else {
                subChoicePanel.add(faceInfo);
            }
            subChoicePanel.revalidate();
            subChoicePanel.repaint();
        });

        // Buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setOpaque(false);
        JButton nextBtn = stylizeButton("Next →");
        JButton resetBtn = stylizeButtonSmall("Reset Deck");
        buttons.add(resetBtn); buttons.add(nextBtn);

        resetBtn.addActionListener(e -> {
            deck.resetToFull();
            updateDeckList();
            JOptionPane.showMessageDialog(this, "Deck reset to full 52 cards.", "Deck Reset", JOptionPane.INFORMATION_MESSAGE);
        });

        nextBtn.addActionListener(e -> {
            try {
                int bet = Integer.parseInt(betField.getText().trim());
                if (bet <= 0) throw new NumberFormatException();
                betAmount = bet;
                String sel = (String) typeBox.getSelectedItem();
                if ("Individual card".equals(sel)){
                    chosenType = ChosenType.INDIVIDUAL;
                    chosenRank = (String) rankBox.getSelectedItem();
                    chosenSuit = Suit.valueOf((String) suitBox.getSelectedItem());
                } else if ("Suit".equals(sel)){
                    chosenType = ChosenType.SUIT;
                    chosenSuit = Suit.valueOf((String) suitBox.getSelectedItem());
                } else if ("Colour".equals(sel)){
                    chosenType = ChosenType.COLOUR;
                    chosenColor = ColorType.valueOf((String) colorBox.getSelectedItem());
                } else {
                    chosenType = ChosenType.FACE;
                }
                enterPlayState();
            } catch (NumberFormatException ex){
                JOptionPane.showMessageDialog(this, "Please enter a valid positive integer bet.", "Invalid input", JOptionPane.ERROR_MESSAGE);
            }
        });

        card.add(inputs, BorderLayout.CENTER);
        card.add(buttons, BorderLayout.SOUTH);

        main.add(card);
        add(main, BorderLayout.CENTER);

        revalidate();
        repaint();
    }

    /* ---------------------- Play screen UI ---------------------- */
    private void enterPlayState(){
        currentState = State.PLAY;
        removeAll();

        // Top toolbar
        JPanel topBar = createTopBar();
        add(topBar, BorderLayout.NORTH);

        // center: large card
        centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        cardComponent.setPreferredSize(new Dimension(380,520));
        JPanel centerWrapper = new RoundedPanel(new Color(40,46,54), 14);
        centerWrapper.setOpaque(false);
        centerWrapper.setBorder(new EmptyBorder(20,20,20,20));
        centerWrapper.setLayout(new BorderLayout());
        centerWrapper.add(cardComponent, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 8));
        bottomBar.setOpaque(false);
        drawButton = stylizeButton("Draw");
        JButton endButton = stylizeButton("End Game");
        JButton shuffleBtn = stylizeButton("Shuffle");
        bottomBar.add(drawButton); bottomBar.add(endButton); bottomBar.add(shuffleBtn);
        centerWrapper.add(bottomBar, BorderLayout.SOUTH);

        centerPanel.add(centerWrapper, BorderLayout.CENTER);

        // Right: smaller & darker tabs for Deck controls + Settings
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBorder(new EmptyBorder(8,8,8,8));
        Color rightBg = new Color(34,38,44); // darker background requested
        tabs.setBackground(rightBg);
        tabs.setForeground(Color.WHITE);
        tabs.setPreferredSize(new Dimension(300, 0)); // smaller width

        // Deck controls tab
        JPanel deckTab = new JPanel();
        deckTab.setOpaque(true);
        deckTab.setBackground(rightBg);
        deckTab.setLayout(new BoxLayout(deckTab, BoxLayout.Y_AXIS));
        deckTab.setBorder(new EmptyBorder(10,10,10,10));

        JLabel controlsTitle = new JLabel("Deck Controls");
        controlsTitle.setForeground(Color.WHITE);
        controlsTitle.setFont(controlsTitle.getFont().deriveFont(Font.BOLD, 16f));
        deckTab.add(controlsTitle);
        deckTab.add(Box.createVerticalStrut(8));

        deckCountLabel = new JLabel("Deck: " + deck.size() + " cards");
        deckCountLabel.setForeground(Color.WHITE);
        deckTab.add(deckCountLabel);
        deckTab.add(Box.createVerticalStrut(10));

        // remove/add suit
        JLabel lbl1 = new JLabel("Remove suit:");
        lbl1.setForeground(Color.WHITE);
        deckTab.add(lbl1);
        deckTab.add(Box.createVerticalStrut(6));
        JPanel removeSuitPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        removeSuitPanel.setOpaque(false);
        JComboBox<String> removeSuitBox = stylizeCombo(new JComboBox<>(new String[]{"HEARTS","DIAMONDS","CLUBS","SPADES"}));
        JButton removeSuitBtn = stylizeButtonSmall("Remove");
        removeSuitPanel.add(removeSuitBox); removeSuitPanel.add(removeSuitBtn);
        deckTab.add(removeSuitPanel);

        removeSuitBtn.addActionListener(e -> {
            Suit s = Suit.valueOf((String) removeSuitBox.getSelectedItem());
            deck.removeSuit(s);
            updateDeckStatus();
        });

        // add suit
        JLabel lbl2 = new JLabel("Add suit:");
        lbl2.setForeground(Color.WHITE);
        deckTab.add(lbl2);
        deckTab.add(Box.createVerticalStrut(6));
        JPanel addSuitPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addSuitPanel.setOpaque(false);
        JComboBox<String> addSuitBox = stylizeCombo(new JComboBox<>(new String[]{"HEARTS","DIAMONDS","CLUBS","SPADES"}));
        JButton addSuitBtn = stylizeButtonSmall("Add");
        addSuitPanel.add(addSuitBox); addSuitPanel.add(addSuitBtn);
        deckTab.add(addSuitPanel);
        addSuitBtn.addActionListener(e -> {
            Suit s = Suit.valueOf((String) addSuitBox.getSelectedItem());
            deck.addSuit(s);
            updateDeckStatus();
        });

        deckTab.add(Box.createVerticalStrut(8));
        // Remove color
        JLabel lbl3 = new JLabel("Remove colour:");
        lbl3.setForeground(Color.WHITE);
        deckTab.add(lbl3);
        deckTab.add(Box.createVerticalStrut(6));
        JPanel removeColPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        removeColPanel.setOpaque(false);
        JComboBox<String> removeColBox = stylizeCombo(new JComboBox<>(new String[]{"RED","BLACK"}));
        JButton removeColBtn = stylizeButtonSmall("Remove");
        removeColPanel.add(removeColBox); removeColPanel.add(removeColBtn);
        deckTab.add(removeColPanel);
        removeColBtn.addActionListener(e -> {
            ColorType color = ColorType.valueOf((String) removeColBox.getSelectedItem());
            deck.removeColor(color);
            updateDeckStatus();
        });

        // Add color
        JLabel lbl4 = new JLabel("Add colour:");
        lbl4.setForeground(Color.WHITE);
        deckTab.add(lbl4);
        deckTab.add(Box.createVerticalStrut(6));
        JPanel addColPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addColPanel.setOpaque(false);
        JComboBox<String> addColBox = stylizeCombo(new JComboBox<>(new String[]{"RED","BLACK"}));
        JButton addColBtn = stylizeButtonSmall("Add");
        addColPanel.add(addColBox); addColPanel.add(addColBtn);
        deckTab.add(addColPanel);
        addColBtn.addActionListener(e -> {
            ColorType color = ColorType.valueOf((String) addColBox.getSelectedItem());
            deck.addColor(color);
            updateDeckStatus();
        });

        deckTab.add(Box.createVerticalStrut(8));
        JLabel lbl5 = new JLabel("Face cards (J,Q,K):");
        lbl5.setForeground(Color.WHITE);
        deckTab.add(lbl5);
        deckTab.add(Box.createVerticalStrut(6));
        JPanel facesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        facesPanel.setOpaque(false);
        JButton removeFacesBtn = stylizeButtonSmall("Remove Faces (J,Q,K)");
        JButton addFacesBtn = stylizeButtonSmall("Add Faces");
        facesPanel.add(removeFacesBtn); facesPanel.add(addFacesBtn);
        deckTab.add(facesPanel);
        removeFacesBtn.addActionListener(e -> { deck.removeFaces(); updateDeckStatus(); });
        addFacesBtn.addActionListener(e -> { deck.addFaces(); updateDeckStatus(); });

        deckTab.add(Box.createVerticalStrut(10));
        JLabel lbl6 = new JLabel("Remove/Add specific card:");
        lbl6.setForeground(Color.WHITE);
        deckTab.add(lbl6);
        JComboBox<String> specificRank = stylizeCombo(new JComboBox<>(RANKS));
        JComboBox<String> specificSuit = stylizeCombo(new JComboBox<>(new String[]{"HEARTS","DIAMONDS","CLUBS","SPADES"}));
        JPanel specificPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        specificPanel.setOpaque(false);
        JButton removeSpecific = stylizeButtonSmall("Remove");
        JButton addSpecific = stylizeButtonSmall("Add");
        specificPanel.add(specificRank); specificPanel.add(specificSuit);
        specificPanel.add(removeSpecific); specificPanel.add(addSpecific);
        deckTab.add(specificPanel);

        removeSpecific.addActionListener(e -> {
            String r = (String) specificRank.getSelectedItem();
            Suit s = Suit.valueOf((String) specificSuit.getSelectedItem());
            boolean changed = deck.removeCard(r,s);
            updateDeckStatus();
            JOptionPane.showMessageDialog(this, changed ? "Card removed." : "That card was not in the deck.", "Specific Remove", JOptionPane.INFORMATION_MESSAGE);
        });
        addSpecific.addActionListener(e -> {
            String r = (String) specificRank.getSelectedItem();
            Suit s = Suit.valueOf((String) specificSuit.getSelectedItem());
            if (!deck.contains(r,s)){
                deck.addCard(r,s);
                updateDeckStatus();
                JOptionPane.showMessageDialog(this, "Card added.", "Specific Add", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "That card already exists in the deck.", "Specific Add", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        deckTab.add(Box.createVerticalStrut(12));
        JButton resetDeckBtn = stylizeButtonSmall("Reset to Full Deck");
        deckTab.add(resetDeckBtn);
        resetDeckBtn.addActionListener(e -> { deck.resetToFull(); updateDeckStatus(); });

        // NOTE: removed small preview from Deck tab per request (no mini panel here)

        tabs.addTab("Deck", deckTab);

        /// ---------------- SETTINGS TAB (fixed layout) ----------------
        JPanel settingsTab = new JPanel(new GridBagLayout());
        settingsTab.setBackground(rightBg);
        settingsTab.setBorder(new EmptyBorder(12,12,12,12));

        GridBagConstraints s = new GridBagConstraints();
        s.insets = new Insets(8,6,8,6);
        s.anchor = GridBagConstraints.WEST;
        s.fill = GridBagConstraints.HORIZONTAL;
        s.weightx = 1.0;

        JLabel settingsTitle = new JLabel("Multipliers");
        settingsTitle.setFont(settingsTitle.getFont().deriveFont(Font.BOLD, 16f));
        settingsTitle.setForeground(Color.WHITE);
        s.gridx = 0; s.gridy = 0; s.gridwidth = 2;
        settingsTab.add(settingsTitle, s);

        s.gridwidth = 1;

        JLabel lblInd = new JLabel("Individual card");
        lblInd.setForeground(Color.WHITE);
        s.gridx = 0; s.gridy = 1;
        settingsTab.add(lblInd, s);

        JTextField fieldInd = makeMulField(mulIndividual);
        s.gridx = 1;
        settingsTab.add(fieldInd, s);

        JLabel lblSuit = new JLabel("Suit");
        lblSuit.setForeground(Color.WHITE);
        s.gridx = 0; s.gridy = 2;
        settingsTab.add(lblSuit, s);

        JTextField fieldSuit = makeMulField(mulSuit);
        s.gridx = 1;
        settingsTab.add(fieldSuit, s);

        JLabel lblCol = new JLabel("Colour");
        lblCol.setForeground(Color.WHITE);
        s.gridx = 0; s.gridy = 3;
        settingsTab.add(lblCol, s);

        JTextField fieldCol = makeMulField(mulColour);
        s.gridx = 1;
        settingsTab.add(fieldCol, s);

        JLabel lblFace = new JLabel("Face cards");
        lblFace.setForeground(Color.WHITE);
        s.gridx = 0; s.gridy = 4;
        settingsTab.add(lblFace, s);

        JTextField fieldFace = makeMulField(mulFace);
        s.gridx = 1;
        settingsTab.add(fieldFace, s);

        // current values row
        JLabel currentLbl = new JLabel("Current values update when applied");
        currentLbl.setForeground(Color.LIGHT_GRAY);
        currentLbl.setFont(currentLbl.getFont().deriveFont(11f));
        s.gridx = 0; s.gridy = 5; s.gridwidth = 2;
        settingsTab.add(currentLbl, s);

        s.gridwidth = 1;

        JButton applyMulBtn = stylizeButton("Apply Multipliers");
        s.gridx = 0; s.gridy = 6; s.gridwidth = 2;
        settingsTab.add(applyMulBtn, s);

        // apply action
        applyMulBtn.addActionListener(e -> {
            try {
                mulIndividual = Double.parseDouble(fieldInd.getText().trim());
                mulSuit       = Double.parseDouble(fieldSuit.getText().trim());
                mulColour     = Double.parseDouble(fieldCol.getText().trim());
                mulFace       = Double.parseDouble(fieldFace.getText().trim());
                JOptionPane.showMessageDialog(this,
                    "Multipliers updated successfully.",
                    "Updated",
                    JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex){
                JOptionPane.showMessageDialog(this,
                    "Enter valid numeric multiplier values.",
                    "Invalid Input",
                    JOptionPane.ERROR_MESSAGE);
            }
        });

        tabs.addTab("Settings", settingsTab);

        // Left: Remaining cards panel (live-updating) - only here
        JPanel leftInfo = new RoundedPanel(new Color(40,46,54), 12);
        leftInfo.setOpaque(false);
        leftInfo.setLayout(new BorderLayout());
        leftInfo.setBorder(new EmptyBorder(12,12,12,12));
        leftInfo.setPreferredSize(new Dimension(220, 0));

        JLabel leftTitle = new JLabel("Remaining Cards");
        leftTitle.setForeground(Color.WHITE);
        leftTitle.setBorder(new EmptyBorder(6,6,6,6));
        leftInfo.add(leftTitle, BorderLayout.NORTH);

        // configure deckList appearance
        deckList.setForeground(Color.WHITE);
        deckList.setBackground(new Color(30,34,40));
        deckList.setSelectionBackground(new Color(70,80,95));
        deckList.setFont(deckList.getFont().deriveFont(12f));

        JScrollPane leftScroll = new JScrollPane(deckList);
        leftScroll.setBorder(BorderFactory.createLineBorder(new Color(60,60,60)));
        leftScroll.setPreferredSize(new Dimension(200, 400));
        leftInfo.add(leftScroll, BorderLayout.CENTER);

        // Layout main content
        JPanel content = new JPanel(new BorderLayout(12,12));
        content.setOpaque(false);
        content.add(centerPanel, BorderLayout.CENTER);
        content.add(tabs, BorderLayout.EAST);    // smaller & darker
        content.add(leftInfo, BorderLayout.WEST);

        add(content, BorderLayout.CENTER);

        // Hook up actions
        drawButton.addActionListener(e -> {
            Card c = deck.drawRandom(rng);
            lastDrawn = c;
            updateDeckStatus();
            updateDeckList();
            cardComponent.setCard(c);
            cardComponent.repaint();
            if (deck.size() == 0) drawButton.setEnabled(false);
            if (c == null) JOptionPane.showMessageDialog(this, "Deck is empty. Reset or add cards.", "Empty Deck", JOptionPane.WARNING_MESSAGE);
        });

        // shuffle action
        shuffleBtn.addActionListener(ev -> {
            deck.shuffle(rng);
            updateDeckStatus();
            updateDeckList();
            JOptionPane.showMessageDialog(this, "Deck shuffled.", "Shuffle", JOptionPane.INFORMATION_MESSAGE);
        });

        endButton.addActionListener(e -> enterResultState());

        // ensure all labels inside these tabs are white (extra safety)
        setLabelsWhite(deckTab);
        setLabelsWhite(settingsTab);

        updateDeckStatus();
        updateDeckList();
        cardComponent.setCard(lastDrawn);
        revalidate();
        repaint();
    }

    // Update the left list with all remaining cards
    private void updateDeckList(){
        deckListModel.clear();
        for (Card c : deck.asList()){
            deckListModel.addElement(c.toString());
        }
    }

    // helper: walk a container and set JLabel foreground to white (ensures contrast)
    private void setLabelsWhite(Container c){
        for (Component comp : c.getComponents()){
            if (comp instanceof JLabel) ((JLabel) comp).setForeground(Color.WHITE);
            if (comp instanceof Container) setLabelsWhite((Container) comp);
        }
    }

    private String chosenSummary(){
        switch (chosenType){
            case INDIVIDUAL: return String.format("Chosen: %s of %s (Individual).", chosenRank, chosenSuit.name());
            case SUIT: return String.format("Chosen suit: %s.", chosenSuit.name());
            case COLOUR: return String.format("Chosen colour: %s.", chosenColor.name());
            default: return "Chosen: face card (J, Q, K).";
        }
    }

    /* ---------------------- Result screen UI ---------------------- */
    private void enterResultState(){
        currentState = State.RESULT;
        removeAll();

        JPanel topBar = createTopBar();
        add(topBar, BorderLayout.NORTH);

        JPanel container = new JPanel(new BorderLayout(18,18));
        container.setOpaque(false);
        container.setBorder(new EmptyBorder(18,18,18,18));

        Card bigCard = lastDrawn;
        CardComponent bigCardComp = new CardComponent();
        bigCardComp.setPreferredSize(new Dimension(520,760));
        bigCardComp.setCard(bigCard);

        boolean won = evaluateWin(bigCard);
        String title = won ? "YOU WON!" : "YOU LOST";
        Color titleColor = won ? new Color(16,140,50) : new Color(200,60,60);

        JPanel right = new RoundedPanel(new Color(40,46,54), 14);
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setBorder(new EmptyBorder(16,16,16,16));

        JLabel resultTitle = new JLabel(title);
        resultTitle.setFont(resultTitle.getFont().deriveFont(Font.BOLD, 36f));
        resultTitle.setForeground(titleColor);
        right.add(resultTitle);
        right.add(Box.createVerticalStrut(14));

        JLabel chosen = new JLabel("Your bet: $" + betAmount + " on " + chosenSummary());
        chosen.setForeground(Color.WHITE);
        right.add(chosen);
        right.add(Box.createVerticalStrut(8));

        double multiplier = getMultiplierForChosen();
        JLabel potential = new JLabel(String.format("Potential payout: $%.2f (bet × %.2f)", betAmount * multiplier, multiplier));
        potential.setForeground(Color.WHITE);
        right.add(potential);
        right.add(Box.createVerticalStrut(8));

        int net = won ? (int) Math.round(betAmount * multiplier) : -betAmount;
        JLabel netLbl = new JLabel((net >= 0 ? "Gained: $" : "Lost: $") + Math.abs(net));
        netLbl.setForeground(net >= 0 ? new Color(18,150,31) : new Color(200,60,60));
        netLbl.setFont(netLbl.getFont().deriveFont(Font.BOLD, 18f));
        right.add(netLbl);
        right.add(Box.createVerticalStrut(16));

        JLabel drawnLbl = new JLabel("Drawn card: " + (bigCard == null ? "None" : bigCard.toString()));
        drawnLbl.setForeground(Color.WHITE);
        right.add(drawnLbl);
        right.add(Box.createVerticalStrut(18));

        JButton restart = stylizeButton("Restart Game");
        restart.addActionListener(e -> { deck.resetToFull(); lastDrawn = null; setupGame(); });
        right.add(restart);
        right.add(Box.createVerticalStrut(8));
        JButton playAgain = stylizeButton("Play Again (keep deck & choice)");
        playAgain.addActionListener(e -> enterPlayState());
        right.add(playAgain);

        container.add(bigCardComp, BorderLayout.WEST);
        container.add(right, BorderLayout.CENTER);

        add(container, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    /* ---------------------- Helpers / logic ---------------------- */
    private boolean evaluateWin(Card drawn){
        if (drawn == null) return false;
        switch (chosenType){
            case INDIVIDUAL:
                return drawn.rank.equals(chosenRank) && drawn.suit == chosenSuit;
            case SUIT:
                return drawn.suit == chosenSuit;
            case COLOUR:
                return drawn.color() == chosenColor;
            default:
                return drawn.isFace();
        }
    }

    private double getMultiplierForChosen(){
        switch (chosenType){
            case INDIVIDUAL: return mulIndividual;
            case SUIT: return mulSuit;
            case COLOUR: return mulColour;
            default: return mulFace;
        }
    }

    private JPanel createTopBar(){
        JPanel top = new JPanel(new BorderLayout(12,0));
        top.setOpaque(false);
        top.setBorder(new EmptyBorder(10,12,10,12));

        JLabel logo = new JLabel("<html><span style='color:#fff;font-weight:bold;font-size:16px;'>Card<span style='color:#2BC0E4;'>Draw</span></span></html>");
        top.add(logo, BorderLayout.WEST);

        topInfoLabel = new JLabel();
        topInfoLabel.setForeground(Color.WHITE);
        updateTopInfo();
        top.add(topInfoLabel, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));
        right.setOpaque(false);
        JButton reset = stylizeButtonSmall("Reset Deck");
        reset.addActionListener(e -> { deck.resetToFull(); updateDeckStatus(); updateDeckList(); });
        right.add(reset);
        JButton help = stylizeButtonSmall("Help");
        help.addActionListener(a -> JOptionPane.showMessageDialog(this,
                "How to play:\n1) Enter bet and choose type on the first screen.\n2) Click Next and use Draw to draw a random card.\n3) Use Deck controls to alter the deck.\n4) End game to see results.\nMultipliers are in Settings (inside the game).",
                "Help", JOptionPane.INFORMATION_MESSAGE));
        right.add(help);

        top.add(right, BorderLayout.EAST);
        return top;
    }

    private void updateTopInfo(){
        String txt = String.format("<html><div style='color:white;padding:6px;'>Bet: $%d &nbsp;&nbsp; | &nbsp;&nbsp; Choice: %s &nbsp;&nbsp; | &nbsp;&nbsp; Deck size: %d</div></html>",
                betAmount, chosenSummary(), deck.size());
        if (topInfoLabel != null) topInfoLabel.setText(txt);
    }

    /* ---------------------- Card painter (unchanged) ---------------------- */
    private static class CardComponent extends JComponent {
        private Card card = null;
        void setCard(Card c){ this.card = c; repaint(); }

        @Override
        protected void paintComponent(Graphics g){
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            g2.setColor(new Color(20,22,26));
            g2.fillRect(0,0,w,h);

            int cardW = Math.min((int) (w * 0.86), 420);
            int cardH = Math.min((int) (h * 0.86), 620);
            int x = (w - cardW)/2, y = (h - cardH)/2;

            g2.setColor(new Color(0,0,0,80));
            g2.fillRoundRect(x+6, y+8, cardW, cardH, 26, 26);

            GradientPaint gp = new GradientPaint(x, y, new Color(255,255,255), x, y+cardH, new Color(240,240,240));
            g2.setPaint(gp);
            RoundRectangle2D.Float rr = new RoundRectangle2D.Float(x,y,cardW,cardH,26,26);
            g2.fill(rr);

            g2.setColor(new Color(150,150,150));
            g2.setStroke(new BasicStroke(2f));
            g2.draw(rr);

            if (card == null){
                g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(14, cardW/12)));
                g2.setColor(new Color(140,140,140));
                String s = "No card drawn";
                FontMetrics fm = g2.getFontMetrics();
                int sw = fm.stringWidth(s);
                g2.drawString(s, x + (cardW-sw)/2, y + cardH/2);
            } else {
                boolean isRed = (card.color() == ColorType.RED);
                Color suitColor = isRed ? new Color(180,40,40) : new Color(40,40,40);

                g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(20, cardW/12)));
                g2.setColor(suitColor);
                g2.drawString(card.rank, x + 18, y + 36);

                g2.setFont(new Font("Serif", Font.PLAIN, Math.max(20, cardW/12)));
                g2.drawString(card.suit.glyph(), x + 18, y + 60);

                if (!card.isFace()){
                    g2.setFont(new Font("Serif", Font.BOLD, Math.max(96, cardW/2)));
                    FontMetrics fmCenter = g2.getFontMetrics();
                    String glyph = card.suit.glyph();
                    int gw = fmCenter.stringWidth(glyph);
                    g2.setColor(suitColor);
                    g2.drawString(glyph, x + (cardW - gw)/2, y + cardH/2 + fmCenter.getAscent()/3);
                } else {
                    drawFaceArt(g2, x, y, cardW, cardH, card.rank, card.suit, suitColor);
                }

                g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(18, cardW/12)));
                String rank = card.rank;
                int sw = g2.getFontMetrics().stringWidth(rank);
                g2.drawString(rank, x + cardW - 18 - sw, y + cardH - 18);
                g2.setFont(new Font("Serif", Font.PLAIN, Math.max(18, cardW/14)));
                String glyph2 = card.suit.glyph();
                int sgw = g2.getFontMetrics().stringWidth(glyph2);
                g2.drawString(glyph2, x + cardW - 18 - sgw, y + cardH - 40);
            }

            g2.dispose();
        }

        private void drawFaceArt(Graphics2D g2, int x, int y, int w, int h, String rank, Suit suit, Color suitColor){
            int px = x + 40, pw = w - 80, py = y + 70, ph = h - 160;
            GradientPaint gp = new GradientPaint(px, py, new Color(245,245,245), px, py+ph, new Color(230,230,230));
            g2.setPaint(gp);
            g2.fillRoundRect(px, py, pw, ph, 18, 18);

            g2.setColor(new Color(200,200,200));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(px, py, pw, ph, 18, 18);

            int cx = px + pw/2;
            int cy = py + ph/3;
            g2.setColor(rank.equals("Q") ? new Color(150,80,200) : new Color(30,40,90));
            g2.fillOval(cx - 48, cy - 60, 96, 96);
            g2.setColor(new Color(245,224,195));
            g2.fillOval(cx - 30, cy - 20, 60, 78);
            g2.setColor(new Color(30,30,30));
            g2.fillOval(cx - 12, cy + 2, 8, 6);
            g2.fillOval(cx + 6, cy + 2, 8, 6);
            g2.drawArc(cx - 10, cy + 26, 20, 10, 0, -180);

            g2.setColor(suit == Suit.HEARTS || suit == Suit.DIAMONDS ? new Color(220,100,110) : new Color(30,80,140));
            Polygon collar = new Polygon();
            collar.addPoint(cx - 50, py + ph - 40);
            collar.addPoint(cx, py + ph - 10);
            collar.addPoint(cx + 50, py + ph - 40);
            g2.fillPolygon(collar);

            g2.setFont(new Font("Serif", Font.BOLD, Math.max(32, pw/10)));
            g2.setColor(suitColor);
            FontMetrics fm = g2.getFontMetrics();
            String glyph = suit.glyph();
            int gw = fm.stringWidth(glyph);
            g2.drawString(glyph, cx - gw/2, py + ph/2 + fm.getAscent()/3);

            AffineTransform orig = g2.getTransform();
            int midY = py + ph/2;
            g2.translate(0, midY*2);
            g2.scale(1, -1);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.08f));
            g2.setFont(new Font("Serif", Font.BOLD, Math.max(72, pw/6)));
            g2.setColor(new Color(0,0,0));
            String big = rank;
            FontMetrics fmb = g2.getFontMetrics();
            int bw = fmb.stringWidth(big);
            g2.drawString(big, cx - bw/2, py + ph/2 + fmb.getAscent()/2);
            g2.setTransform(orig);
            g2.setComposite(AlphaComposite.SrcOver);
        }
    }

    /* ---------------------- Small UI helpers (styles) ---------------------- */
    private JButton stylizeButton(String text){
        JButton b = new JButton(text);
        b.setUI(new BasicButtonUI());
        b.setBackground(accent);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(8,14,8,14));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
    private JButton stylizeButtonSmall(String text){
        JButton b = new JButton(text);
        b.setUI(new BasicButtonUI());
        b.setBackground(new Color(70,80,90));
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(6,8,6,8));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
    private JTextField stylizeField(JTextField f){
        f.setOpaque(true);
        f.setBackground(new Color(245,245,245));
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200,200,200)),
                new EmptyBorder(6,6,6,6)
        ));
        return f;
    }
    private JComboBox<String> stylizeCombo(JComboBox<String> c){
        c.setBackground(Color.WHITE);
        return c;
    }

    /* ---------------------- Utility methods ---------------------- */
    private void shuffleBtnAction(JButton shuffleBtn){
        shuffleBtn.addActionListener(e -> {
            deck.shuffle(rng);
            updateDeckStatus();
            updateDeckList();
            JOptionPane.showMessageDialog(this, "Deck shuffled.", "Shuffle", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private void updateDeckStatus(){
        deckCountLabel.setText("Deck: " + deck.size() + " cards");
        updateDeckList();
        drawButton.setEnabled(deck.size() > 0);
        revalidate();
        repaint();
    }

    /* ---------------------- Utility classes ---------------------- */
    private static class RoundedPanel extends JPanel {
        private final Color bg;
        private final int radius;
        RoundedPanel(Color bg, int r){
            super();
            this.bg = bg; this.radius = r;
            setOpaque(false);
        }
        @Override protected void paintComponent(Graphics g){
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Shape r = new RoundRectangle2D.Float(0,0,getWidth(),getHeight(),radius,radius);
            g2.setColor(bg);
            g2.fill(r);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /* ---------------------- Result / paint ---------------------- */
    @Override
    public void paintComponent(Graphics g){
        super.paintComponent(g);
        if (currentState == State.PLAY) updateTopInfo();
    }
}
