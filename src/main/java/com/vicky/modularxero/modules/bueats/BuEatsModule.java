package com.vicky.modularxero.modules.bueats;

import com.vicky.modularxero.AbstractModule;
import com.vicky.modularxero.Module;
import com.vicky.modularxero.ModuleMetrics;
import com.vicky.modularxero.modules.bueats.models.*;
import com.vicky.modularxero.common.MessageType;
import com.vicky.modularxero.common.Response;
import com.vicky.modularxero.common.values.ListValue;
import com.vicky.modularxero.common.values.MapValue;
import com.vicky.modularxero.common.values.MessageValue;
import com.vicky.modularxero.common.values.StringValue;
import com.vicky.modularxero.modules.bueats.dao.StaticDaoHolder;
import com.vicky.modularxero.common.util.PossibleAccessionException;
import com.fasterxml.jackson.databind.JsonNode;

import org.hibernate.SessionFactory;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BuEatsModule extends AbstractModule {
    private final ModuleMetrics metrics = new ModuleMetrics();
    public static SessionFactory buSF;

    private Response<MapValue<MessageValue<?>>> handleLogin(JsonNode payload) {
        boolean isCafeteria = payload.get("isCaf").get("value").asBoolean();
        if (isCafeteria) {

            String cafNumber = payload.get("cafNumber").get("value").asText(),
                    password = payload.get("potentialPassword").get("value").asText();

            PossibleAccessionException<Cafeteria> accessionException =
                    StaticDaoHolder.cafeteriaDao.attemptLogin(cafNumber, password);

            if (accessionException.isAddedSuccseffuly()) {
                Response<MapValue<MessageValue<?>>> resp = new Response<>();
                resp.type = MessageType.LOGIN;
                resp.payload = new MapValue<>(
                        Map.of(
                                "name", new StringValue(accessionException.getPassableObject().getCafeteriaName()),
                                "earnings", new StringValue(accessionException.getPassableObject().getEarnings().toString()),
                                "cafNo", new StringValue(accessionException.getPassableObject().getCafNumber()),
                                "orders", new ListValue<>(StaticDaoHolder.cafeteriaDao.getCafeteriaOrders(cafNumber).stream().map((StudentOrder::asjsonString)).collect(Collectors.toList()))
                        )
                );
                resp.status = Response.ResponseStatus.OK;
                return resp;
            }
            else {
                Response<MapValue<MessageValue<?>>> resp = new Response<>();
                resp.type = MessageType.LOGIN;
                resp.payload = new MapValue<>(
                        Map.of(
                                "reason", new StringValue(accessionException.getReason())
                        )
                );
                resp.status = Response.ResponseStatus.FAILED;
                return resp;
            }
        }
        else {
            String matricNumber = payload.get("matricNumber").get("value").asText(),
                    password = payload.get("potentialPassword").get("value").asText();

            PossibleAccessionException<Student> accessionException =
                    StaticDaoHolder.studentDao.attemptLogin(matricNumber, password);

            if (accessionException.isAddedSuccseffuly()) {
                Response<MapValue<MessageValue<?>>> resp = new Response<>();
                resp.type = MessageType.LOGIN;
                resp.payload = new MapValue<>(
                        Map.of(
                            "hostel", new StringValue(accessionException.getPassableObject().getStudentsHostel().getHostelName()),
                            "cafeterias", new ListValue<>(StaticDaoHolder.cafeteriaDao.findAll().stream().map((Cafeteria::getCafeteriaName)).collect(Collectors.toList()))
                        )
                );
                resp.status = Response.ResponseStatus.OK;
                return resp;
            }
            else {
                Response<MapValue<MessageValue<?>>> resp = new Response<>();
                resp.type = MessageType.LOGIN;
                resp.payload = new MapValue<>(
                        Map.of(
                                "reason", new StringValue(accessionException.getReason())
                        )
                );
                resp.status = Response.ResponseStatus.FAILED;
                return resp;
            }
        }
    }

    @Override
    public @NotNull String getName() {
        return "bu_eats";
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public void pause() {

    }

    @Override
    public Response<MapValue<MessageValue<?>>> handleRequest(@NotNull JsonNode node) {
        MessageType type = MessageType.valueOf(node.get("type").asText());
        switch (type) {
            case LOGIN:
                return handleLogin(node.get("payload").get("value"));
            default: return null;
        }
    }

    @NotNull
    @Override
    public ModuleMetrics getMetrics() {
        return metrics;
    }

    @NotNull
    @Override
    public List<Class<?>> getModuleAnnotatedClasses() {
        return List.of(Cafeteria.class, CafFoodItemPriceMap.class, CafSupportedHostelDelivery.class, FoodItem.class, Hostel.class, HostelPriceMap.class, Student.class, StudentOrder.class);
    }

    @Override
    public boolean autoStart() {
        return true;
    }

    @Override
    public void setSessionFactory(@NotNull SessionFactory factory) {
        buSF = factory;
    }
}
