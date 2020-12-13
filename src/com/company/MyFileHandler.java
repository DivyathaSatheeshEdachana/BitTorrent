package com.company;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

public class MyFileHandler {
    

    byte[] bitField;
    boolean iHaveFile;
    int selfId;
    public MyFileHandler(Boolean iHaveFile, int selfId){
        this.iHaveFile = iHaveFile;
        this.selfId = selfId;


        bitFieldSetUp();
        if(iHaveFile){
            try {
                splitFile(CommonConfigHandler.getInstance().getProjectConfiguration().getFileName());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
      //  setDirectory();
    }

    private void setDirectory() {
        String path = "peer_"+selfId;
        File file = new File(path);
        file.mkdir();
    }


    private void bitFieldSetUp() {
        ProjectConfiguration projectConfiguration = CommonConfigHandler.getInstance().getProjectConfiguration();
        bitField = new byte[Util.convertNumBitsToNumBytes(projectConfiguration.getNumChunks())];
        try {
            if(iHaveFile){
                BitSet set = BitSet.valueOf(bitField);
                for(int i = 0; i < projectConfiguration.getNumChunks(); i++){
                    set.set(i,true);
                }
                bitField = set.toByteArray();
                splitFile(CommonConfigHandler.getInstance().getProjectConfiguration().getFileName());
            }
        } catch (IOException e) {
            System.out.println("Error with splitting the file");
            e.printStackTrace();
        }

    }

    public int numOfChunksIHave(){
        BitSet set = BitSet.valueOf(bitField);
        int c = 0;
        for(int i =0; i < set.length(); i++){
            if(set.get(i)){
                c++;
            }
        }
        return c;
    }
    // Missing pieces
    public List<Integer> chunksIWant(){
        BitSet myBitSet = BitSet.valueOf(bitField);
        List<Integer> list = new ArrayList<Integer>();
        for(int i = 0; i < CommonConfigHandler.getInstance().getProjectConfiguration().getNumChunks(); i++){
            if(!myBitSet.get(i)){
                list.add(i);
            }
        }
        return list;
    }



    public List<Integer> chunksIAmInterestedInFromPeer(byte[] peerBitField){
        BitSet peerBitSet = BitSet.valueOf(peerBitField);
        BitSet myBitSet = BitSet.valueOf(bitField);
        List<Integer> list = new ArrayList<>();
        for(int i = 0; i < peerBitSet.length(); i++){
            if(peerBitSet.get(i) && (!myBitSet.get(i))){
                list.add(i);
            }
        }
        return list;
    }



    public byte[] getBitField(){
        return bitField;
    }


    public  void splitFile(String fName) throws IOException {
        File f = new File(selfId +"/"+fName);
        int partCounter = 0;//I like to name parts from 001, 002, 003, ...
        //you can change it to 0 if you want 000, 001, ...

        int sizeOfFiles = CommonConfigHandler.getInstance().getProjectConfiguration().getPieceSize();
        byte[] buffer = new byte[sizeOfFiles];

        String fileName = f.getName();

        //try-with-resources to ensure closing stream
        try (FileInputStream fis = new FileInputStream(f);
             BufferedInputStream bis = new BufferedInputStream(fis)) {

            int bytesAmount = 0;
            while ((bytesAmount = bis.read(buffer)) > 0) {
                //write each chunk of data into separate file with different number in name
                String filePartName = fileName + "." + partCounter;
                File newFile = new File(f.getParent(), filePartName);
                try (FileOutputStream out = new FileOutputStream(newFile)) {
                    out.write(buffer, 0, bytesAmount);
                }
                partCounter++;
            }
        }
    }


    public  List<File> listOfFilesToMerge(String oneOfFiles) {
        return listOfFilesToMerge(new File(oneOfFiles));
    }

    public  void mergeFiles(String oneOfFiles, String into) throws IOException{
        mergeFiles(new File(oneOfFiles), new File(into));
    }

    public  void mergeFiles(File oneOfFiles, File into)
            throws IOException {
        mergeFiles(listOfFilesToMerge(oneOfFiles), into);
    }
    public static List<File> listOfFilesToMerge(File oneOfFiles) {
        String tmpName = oneOfFiles.getName();//{name}.{number}
        String destFileName = tmpName.substring(0, tmpName.lastIndexOf('.'));//remove .{number}
        File[] files = oneOfFiles.getParentFile().listFiles(
                (File dir, String name) -> name.matches(destFileName + "[.]\\d+"));
        Arrays.sort(files);//ensuring order 001, 002, ..., 010, ...
        return Arrays.asList(files);
    }

    private void mergeFiles(List<File> files, File into)
            throws IOException {
        try (FileOutputStream fos = new FileOutputStream(into);
             BufferedOutputStream mergingStream = new BufferedOutputStream(fos)) {
            for (File f : files) {
                Files.copy(f.toPath(), mergingStream);
            }
        }
    }


    synchronized public void updateMyBitfiled(int pieceIndex){

        //bitfieldLock.lock();
        try {
            // Do bit manipulation here
            BitSet bitSet = BitSet.valueOf(bitField);
            bitSet.set(pieceIndex, true);
            bitField = bitSet.toByteArray();

        }
        catch (Exception e){
            e.printStackTrace();
        }
        finally {
           // bitfieldLock.unlock();
        }
    }






    public static synchronized boolean canITerminate() {

        String line;
        int hasFileCount = 1;

        try {
            BufferedReader in = new BufferedReader(new FileReader(
                    "PeerInfo.cfg"));

            while ((line = in.readLine()) != null) {
                hasFileCount = hasFileCount
                        * Integer.parseInt(line.trim().split("\\s+")[3]);
            }
            if (hasFileCount == 0) {
                in.close();
                return false;
            } else {
                in.close();
                return true;
            }

        } catch (Exception e) {
            return false;
        }

    }

    synchronized public void putChunk(int pieceIndex, byte[] fileData){
        try {

            String path = selfId +"/"+ CommonConfigHandler.getInstance().getProjectConfiguration().getFileName() + "." + pieceIndex;
            File file = new File(path);
            OutputStream
                    os
                    = new FileOutputStream(file);

            // Starts writing the bytes in it
            os.write(fileData);
            System.out.println("Successfully"
                    + " byte inserted");

            // Close the file
            os.close();
            updateMyBitfiled(pieceIndex);
            if(checkIfFinish()){
                mergeFiles(path, selfId +"/" + CommonConfigHandler.getInstance().getProjectConfiguration().getFileName());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public  byte[] getChunk(int pieceIndex){
        byte[] data = null;

        try {
            String fPath = selfId+"/"+CommonConfigHandler.getInstance().getProjectConfiguration().getFileName() + "." + pieceIndex;
            Path path = Paths.get(fPath);
            data = Files.readAllBytes(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return data;
    }

    public boolean checkIfFinish(){
        ProjectConfiguration projectConfiguration = CommonConfigHandler.getInstance().getProjectConfiguration();
        int totalChunks = projectConfiguration.getNumChunks();
        BitSet set = BitSet.valueOf(bitField);
        if(set.length() == 0){
            return false;
        }
        if(set.length() < totalChunks){
            return false;
        }
        for(int i = 0; i < set.length(); i++){
            if(!set.get(i)){
                return false;
            }
        }

        return true;

    }


}