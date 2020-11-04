package telnetthespire;

import basemod.BaseMod;
import basemod.ModButton;
import basemod.ModLabel;
import basemod.ModPanel;
import basemod.eventUtil.AddEventParams;
import basemod.eventUtil.EventUtils;
import basemod.interfaces.PostDungeonUpdateSubscriber;
import basemod.interfaces.PostInitializeSubscriber;
import basemod.interfaces.PostUpdateSubscriber;
import basemod.interfaces.PreUpdateSubscriber;
import com.evacipated.cardcrawl.modthespire.lib.SpireConfig;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.shrines.FaceTrader;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import telnetthespire.patches.InputActionPatch;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static java.lang.System.exit;
@SuppressWarnings("unchecked")
@SpireInitializer
public class TelnetTheSpire implements PostInitializeSubscriber, PostUpdateSubscriber, PostDungeonUpdateSubscriber, PreUpdateSubscriber {

    private static Thread server;
    private static final Logger logger = LogManager.getLogger(TelnetTheSpire.class.getName());
    // private static Thread writeThread;
    private static BlockingQueue<HashMap<String, Object>> stateQueue;
    // private static Thread readThread;
    private static BlockingQueue<String> readQueue;
    public static boolean mustSendGameState = false;

    private static SpireConfig serverConfig;
    private static final String IP_OPTION = "192.168.0.161";
    private static final String PORT_OPTION = "PORT";
    private static final String COLOR_OPTION = "color";
    private static final String DEFAULT_IP = "192.168.0.161";
    private static final Integer DEFAULT_PORT = 9002;
    private static final Integer DEFAULT_BACKLOG = 10;
    private static final Boolean DEFAULT_COLOR = true;

    public TelnetTheSpire(){
        BaseMod.subscribe(this);

        try {
            serverConfig = new SpireConfig("TelnetTheSpire", "config");
	    if(serverConfig.getString(IP_OPTION) == null)
		serverConfig.setString(IP_OPTION, DEFAULT_IP);
	    if(serverConfig.getString(PORT_OPTION) == null)
		serverConfig.setInt(PORT_OPTION, DEFAULT_PORT);
	    if(serverConfig.getString(COLOR_OPTION) == null)
		serverConfig.setBool(COLOR_OPTION, DEFAULT_COLOR);
            serverConfig.save();
        } catch (IOException e) {
            e.printStackTrace();
        }

	startServer();
    }

    public static void initialize() {
        TelnetTheSpire mod = new TelnetTheSpire();
    }

    public void receivePreUpdate() {
        if(messageAvailable()) {
            try {
                boolean stateChanged = CommandExecutor.executeCommand(readMessage());
                if(stateChanged) {
                    GameStateListener.registerCommandExecution();
                }
            } catch (InvalidCommandException e) {
                HashMap<String, Object> error = new HashMap<>();
                error.put("error", e.getMessage());
                error.put("ready_for_command", GameStateListener.isWaitingForCommand());
                sendState(error);
            }
        }
    }

    public void receivePostInitialize() {
        setUpOptionsMenu();
	BaseMod.addEvent((new AddEventParams.Builder(FaceTrader.ID, FaceTrader.class))
			 .eventType(EventUtils.EventType.FULL_REPLACE).overrideEvent("Match and Keep!")
			 .create());
    }

    public void receivePostUpdate() {
        if(!mustSendGameState && GameStateListener.checkForMenuStateChange()) {
            mustSendGameState = true;
        }
        if(mustSendGameState) {
            sendGameState();
            mustSendGameState = false;
        }
        InputActionPatch.doKeypress = false;
    }

    public void receivePostDungeonUpdate() {
        if (GameStateListener.checkForDungeonStateChange()) {
            mustSendGameState = true;
        }
        if(AbstractDungeon.getCurrRoom().isBattleOver) {
            GameStateListener.signalTurnEnd();
        }
    }

    private void setUpOptionsMenu() {
        ModPanel settingsPanel = new ModPanel();

        ModLabel ipLabel = new ModLabel(
                "", 350, 700, Settings.CREAM_COLOR, FontHelper.charDescFont,
                settingsPanel, modLabel -> {
                    modLabel.text = String.format("TCP Server IP: %s", getIPString());
                });
        settingsPanel.addUIElement(ipLabel);

	ModLabel portLabel = new ModLabel(
                "", 350, 650, Settings.CREAM_COLOR, FontHelper.charDescFont,
                settingsPanel, modLabel -> {
                    modLabel.text = String.format("TCP Server port: %d", getPort());
                });
        settingsPanel.addUIElement(portLabel);

	ModLabel colorLabel = new ModLabel(
                "", 350, 600, Settings.CREAM_COLOR, FontHelper.charDescFont,
                settingsPanel, modLabel -> {
                    modLabel.text = String.format("Enable color: %s", getColorOption());
                });
        settingsPanel.addUIElement(colorLabel);
	
        BaseMod.registerModBadge(ImageMaster.loadImage("Icon.png"),"Telnet The Spire", "The Cultist", null, settingsPanel);
    }

    private void startServerThread() {
        stateQueue = new LinkedBlockingQueue<>();
        readQueue = new LinkedBlockingQueue<>();
	server = new Thread(new SlayTheSpireServer(getPort(), DEFAULT_BACKLOG, getIPString(), stateQueue, readQueue, getColorOption()));
        server.start();
    }

    private static void sendGameState() {
        HashMap<String, Object> state = GameStateConverter.getCommunicationState();
        sendState(state);
    }

    public static void dispose() {
        logger.info("Shutting down child process...");
    }

    private static void sendState(HashMap<String, Object> state) {
        if(stateQueue != null && server.isAlive()) {
            stateQueue.add(state);
        }
    }

    private static boolean messageAvailable() {
        return readQueue != null && !readQueue.isEmpty();
    }

    private static String readMessage() {
        if(messageAvailable()) {
	    String message = readQueue.remove();
	    if(message.equals("exit"))
		exit(0);
	    return message;
        } else {
            return null;
        }
    }

    private static String getIPString() {
        if (serverConfig == null)
            return DEFAULT_IP;
        return serverConfig.getString(IP_OPTION).trim();
    }

    private static int getPort() {
	if (serverConfig == null)
            return DEFAULT_PORT;

        return serverConfig.getInt(PORT_OPTION);
    }

    private static Boolean getColorOption() {
	if (serverConfig == null)
	    return DEFAULT_COLOR;
	return serverConfig.getBool(COLOR_OPTION);
    }

    private boolean startServer() {
	startServerThread();
	if (GameStateListener.isWaitingForCommand()) {
	    mustSendGameState = true;
	}
	return true;
    }   
}
