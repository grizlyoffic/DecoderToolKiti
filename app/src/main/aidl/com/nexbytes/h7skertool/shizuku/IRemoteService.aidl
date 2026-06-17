package com.nexbytes.h7skertool.shizuku;

interface IRemoteService {
    void createFile(String path, String content);
    boolean fileExists(String path);
    void deleteFile(String path);
    String readFile(String path);
    int getVersion();
}
