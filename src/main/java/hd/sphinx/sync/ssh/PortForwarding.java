package hd.sphinx.sync.ssh;

import java.util.Hashtable;

import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import hd.sphinx.sync.Main;
import hd.sphinx.sync.mysql.MySQL;
import hd.sphinx.sync.util.ConfigManager;

public class PortForwarding {
    public static boolean enable = ConfigManager.getBoolean("ssh.use");
    private static String dbHost = ConfigManager.getString("ssh.dbhost");
    private static int dbPort = Integer.parseInt(ConfigManager.getString("ssh.dbport"));
    private static String sshHost = ConfigManager.getString("ssh.sshhost");
    private static int sshPort = Integer.parseInt(ConfigManager.getString("ssh.sshport"));
    private static String username = ConfigManager.getString("ssh.username");
    private static String password = ConfigManager.getString("ssh.password");
    private static String identity = ConfigManager.getString("ssh.identity");
    private static boolean paramSet = false;
    private static Session session = null;
    private static int localport = -1;
    private static AuthType authType = getAuthType();
    private static final JSch jsch = new JSch();
    private static Hashtable<String,String> config = new Hashtable<>();

    private enum AuthType{
        PASSWORD,
        PUBLICKEY
    } 

    private static AuthType getAuthType(){
        final String authStr = ConfigManager.getString("ssh.auth");
        if(authStr.matches("publickey")){
            return AuthType.PUBLICKEY;
        }else if(authStr.matches("password")){
            return AuthType.PASSWORD;
        }else{
            return null;
        }
    }

    private static void setParams(){
        // check params
        try{
            if(sshPort < 0 || sshPort > 65535){
                throw new InvalidConfigurationException("ssh port must be in a range of (0-65535)");
            }

            if(dbPort < 0 || dbPort > 65535){
                throw new InvalidConfigurationException("remote port must be in a range of (0-65535)");
            }

            if(authType == null){
                throw new InvalidConfigurationException("auth type is invalid. only 'password' or 'publickey' is allowed.");
            }

            if(username.length()>31){
                throw new InvalidConfigurationException("username is too long. please specify within 31 characters.");
            }

            if(authType==AuthType.PASSWORD&&password.length()>128){
                throw new InvalidConfigurationException("password is too long. please specify within 128 characters.");
            }

        }catch(InvalidConfigurationException exception){
            Main.logger.severe("Exception occured during loading port-forwarding setting.");
            exception.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(Main.main);
        }
        try{
            if(authType==AuthType.PUBLICKEY){
                if(password==null||password==""){
                    jsch.addIdentity(identity);
                }else{
                    jsch.addIdentity(identity, password);
                }
            }
        }catch(JSchException exception){
            exception.printStackTrace();
        }
        config.put("StrictHostKeyChecking", "no");
        paramSet = true;
    }

    private static Session createSession(){
        discardTunnel();
        try{
            session = jsch.getSession(username, sshHost, sshPort);
            session.setConfig(config);
            session.setPassword(password);
            session.connect();
            return session;
        }catch(JSchException exception){
            exception.printStackTrace();
        }
        return null;
    }

    private static boolean createTunnel(){
        try{
            if(session!=null&&localport==-1){
                localport = session.setPortForwardingL(Integer.parseInt(MySQL.port), dbHost, dbPort);
            }
            return true;
        }catch(JSchException exception){
            Main.logger.warning(exception.getMessage());
        }
        return false;
    }

    private static void discardTunnel(){
        try{
            if(session!=null&&localport>=0&&localport<=65535){
                session.delPortForwardingL(localport);
            }
        }catch(JSchException exception){
            Main.logger.warning(exception.getMessage());
        }finally{
            localport=-1;
        }
    }

    public static boolean connect(){
        if(!paramSet){
            setParams();
        }
        if(!isConnected()){
            if(createSession()==null){
                Main.logger.severe("Failed to create SSH session.");
            }
            if(createTunnel()==false){
                Main.logger.severe("Failed to create SSH tunnel.");
            }
        }
        return isConnected();
    }

    public static void disconnect(){
        if(localport!=-1){
            discardTunnel();
        }
        if(session.isConnected()){
            session.disconnect();
        }
    }

    public static boolean isConnected(){
        if(session == null) {
            return false;
        }
        return session.isConnected()&&localport!=-1;
    }

}
