package com.csaproject.gehu.myapplication;

import java.io.Serializable;

public class entry implements Serializable {
    private  int ID;
    private String name,path;

    public entry(String name, String path) {
        this.name = name;
        this.path = path;
    }

    public entry() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getID() {
        return ID;
    }

    public void setID(int ID) {
        this.ID = ID;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
