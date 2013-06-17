
package com.sic.plugins.kpp.model;

import hudson.DescriptorExtensionList;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.XmlFile;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.lang.StringUtils;

/**
 *
 * An extension point for providing {@link KPPKeychain}
 */

public abstract class KPPKeychainsProvider implements ExtensionPoint {
    
    private final static Logger LOGGER = Logger.getLogger(KPPKeychainsProvider.class.getName());
    private final static String DEFAULT_KEYCHAINS_CONFIG_XML = "com.sic.kpp.KPPKeychainProvider.xml";
    private final static String DEFAULT_KEYCHAINS_UPLOAD_DIRECTORY_PATH = Hudson.getInstance().getRootDir() + File.separator + "kpp_upload";
    
    private List<KPPKeychain> keychains = new ArrayList<KPPKeychain>();
    
    /**
     * Constructor
     */
    public KPPKeychainsProvider() {
        load();
        try {
            save();
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Could not save keychains config file", ex);
        }
    }
    
    /**
     * load keychains.
     */
    private void load() {
        
        // 1. load keychain(s) information from config xml.
        try {
            XmlFile xml = getKeychainsConfigFile();
            if (xml.exists()) {
                xml.unmarshal(this);
            }
        } catch (FileNotFoundException e) {
            LOGGER.log(Level.SEVERE, "No keychains config file found.", e);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to read the existing keychains and provisioning profiles from xml", e);
        }
        
        List<KPPKeychain> keychainsFromXml = keychains;
        
        // 2. load keychains from upload folder.
        List<KPPKeychain> keychainsFromFolder = loadKeychainsFromUploadFolder();
        
        //3. merge keychains
        keychains = mergeKeychains(keychainsFromXml, keychainsFromFolder);
    }
    
    private List<KPPKeychain> loadKeychainsFromUploadFolder() {
        checkAndCreateKeychainUploadFolder();
        List<KPPKeychain> k = new ArrayList<KPPKeychain>();
        
        File[] keychainFiles = new File(getKeychainsUploadDirectoryPath()).listFiles(new KPPKeychainsProvider.KeychainFileNameFilter());
        for(File keychainFile : keychainFiles) {
            KPPKeychain keychain = new KPPKeychain(keychainFile.getName());
            if(StringUtils.isBlank(keychain.getFileName())) {
            break;
            }
            k.add(keychain);
        }
        return k;
    }
    
    private List<KPPKeychain> mergeKeychains(List<KPPKeychain>keychainsFromXML, List<KPPKeychain>keychainsFromFolder) {
        List<KPPKeychain> k = new ArrayList<KPPKeychain>();
        
        // 1. add each keychain to the list, which occurs in both lists.
        List<KPPKeychain> kNotListed = new ArrayList<KPPKeychain>();
        for (KPPKeychain keychainFolder : keychainsFromFolder) {
            boolean isKeychainNotInXML = true;
            for (KPPKeychain keychainXML : keychainsFromXML) {
                if (keychainXML.equals(keychainFolder)) {
                    isKeychainNotInXML = false;
                    k.add(keychainXML);
                    break;
                }
            }
            
            if (isKeychainNotInXML) {
                kNotListed.add(keychainFolder);
            }
        }
        
        // 2. add each keychain from folder to the list, which is not 
        if (kNotListed.isEmpty()==false) {
            k.addAll(kNotListed);
        }
        
        return k;
    }
    
    private void checkAndCreateKeychainUploadFolder() {
        File uploadFolder = new File(getKeychainsUploadDirectoryPath());
        if (!uploadFolder.exists()) {
            uploadFolder.mkdir();
        }
    }
    

    /**
     * Returns all the registered {@link KPPKeychain} descriptors.
     *
     * @return all the registered {@link KPPKeychain} descriptors.
     */
    public static DescriptorExtensionList<KPPKeychain, Descriptor<KPPKeychain>> allKeychainDescriptors() {
        return Hudson.getInstance().getDescriptorList(KPPKeychain.class);
    }

    /**
     * Get a list with all keychains.
     * 
     * @return all keychains.
     */
    public List<KPPKeychain> getKeychains() {
        return keychains;
    }

    /**
     * Get the keychains config file.
     * 
     * @return file.
     */
    public XmlFile getKeychainsConfigFile() {
        return new XmlFile(new File(Hudson.getInstance().getRootDir(), getKeychainsConfigXMLName()));
    }

    /**
     * All regsitered instances.
     */
    public static ExtensionList<KPPKeychainsProvider> all() {
        return Jenkins.getInstance().getExtensionList(KPPKeychainsProvider.class);
    }

    /**
     * Get the keychains xml config filename.
     * 
     * @return filename.
     */
    public String getKeychainsConfigXMLName() {
        return DEFAULT_KEYCHAINS_CONFIG_XML;
    }
    
    /**
     * Get the keychains upload directory path.
     * @return directory path.
     */
    public String getKeychainsUploadDirectoryPath() {
        return DEFAULT_KEYCHAINS_UPLOAD_DIRECTORY_PATH;
    }
    
    public final void save() throws IOException {
        getKeychainsConfigFile().write(this);
        update();
    }
    
    /**
     * Updates keychains information from xml configuration and keychain upload folder.
     */
    public void update() {
        getKeychains().clear();
        load();
    }
    
    /**
     * Store uploaded keychain file inside upload directory.
     * 
     * @param keychainFileItemToUpload
     * @throws FileNotFoundException
     * @throws IOException 
     */
    public void uploadKeychain(FileItem keychainFileItemToUpload) throws FileNotFoundException, IOException {
        // save uploaded file
        byte[] fileData = keychainFileItemToUpload.get();
        File toUploadFile = new File(getKeychainsUploadDirectoryPath(), keychainFileItemToUpload.getName());
        OutputStream os = new FileOutputStream(toUploadFile);
        os.write(fileData);
    }
    
    /**
     * 
     * @param keychainsFromSave 
     */
    public void updateKeychainsFromSave(List<KPPKeychain>keychainsFromSave) {
        List<KPPKeychain> currentKeychains = getKeychains();
        
        for (KPPKeychain keychainFromSave : keychainsFromSave) {
            for (KPPKeychain currentKeychain : currentKeychains) {
                if (currentKeychain.equals(keychainFromSave)) {
                    currentKeychain.setDescription(keychainFromSave.getDescription());
                    currentKeychain.setPassword(keychainFromSave.getPassword());
                    break;
                }
            }
        }
        keychains = currentKeychains;
    }
    
    private class KeychainFileNameFilter implements FilenameFilter {

        public boolean accept(File file, String name) {
            boolean ret = false;
            if (file.isDirectory() && name.endsWith(".keychain")) { // keychains are directories
                ret = true;
            }
            return ret;
        }
    }
}