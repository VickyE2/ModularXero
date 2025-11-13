package com.vicky.modularxero.modules.bueats.dao;

import com.vicky.modularxero.modules.bueats.models.Hostel;
import com.vicky.modularxero.common.GenericDao;

import static com.vicky.modularxero.modules.bueats.BuEatsModule.buSF;

public class HostelDao extends GenericDao<Hostel, String> {
    public HostelDao() {
        super(Hostel.class, buSF);
    }
}
