/* Copyright (c) 2016 Jesper Öqvist <jesper@llbit.se>
 *
 * This file is part of Chunky.
 *
 * Chunky is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Chunky is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with Chunky.  If not, see <http://www.gnu.org/licenses/>.
 */
package se.llbit.chunky.ui.render;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.stage.FileChooser;
import se.llbit.chunky.renderer.scene.PlayerModel;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.chunky.ui.DoubleAdjuster;
import se.llbit.chunky.ui.RenderControlsFxController;
import se.llbit.chunky.world.entity.Entity;
import se.llbit.chunky.world.entity.PlayerEntity;
import se.llbit.json.JsonObject;
import se.llbit.log.Log;
import se.llbit.math.QuickMath;
import se.llbit.math.Vector3;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Consumer;

public class EntitiesTab extends Tab implements RenderControlsTab, Initializable {
  private Scene scene;

  static class PlayerData {
    public final PlayerEntity entity;
    public final String name;

    public PlayerData(PlayerEntity entity, Scene scene) {
      this.entity = entity;

      JsonObject profile = scene.getPlayerProfile(entity);
      name = getName(profile);
    }

    private static String getName(JsonObject profile) {
      return profile.get("name").stringValue("Unknown");
    }

    @Override public String toString() {
      return entity.uuid;
    }

    @Override public int hashCode() {
      return entity.uuid.hashCode();
    }

    @Override public boolean equals(Object obj) {
      // Identity comparison is used to ensure that the table in the
      // entities tab is properly updated after rebuilding the scene.
      if (obj instanceof PlayerData) {
        return ((PlayerData) obj).entity == entity;
      }
      return false;
    }
  }

  @FXML private TableView<PlayerData> entityTable;
  @FXML private TableColumn<PlayerData, String> nameCol;
  @FXML private TableColumn<PlayerData, String> idCol;
  @FXML private Button delete;
  @FXML private Button add;
  @FXML private Button cameraToPlayer;
  @FXML private Button playerToCamera;
  @FXML private Button playerToTarget;
  @FXML private Button faceCamera;
  @FXML private Button faceTarget;
  @FXML private ChoiceBox<PlayerModel> playerModel;
  @FXML private TextField skin;
  @FXML private Button selectSkin;
  @FXML private DoubleAdjuster direction;
  @FXML private DoubleAdjuster headYaw;
  @FXML private DoubleAdjuster headPitch;
  @FXML private DoubleAdjuster leftArmPose;
  @FXML private DoubleAdjuster rightArmPose;
  @FXML private DoubleAdjuster leftLegPose;
  @FXML private DoubleAdjuster rightLegPose;
  @FXML private DoubleAdjuster scale;

  public EntitiesTab() throws IOException {
    FXMLLoader loader = new FXMLLoader(getClass().getResource("EntitiesTab.fxml"));
    loader.setRoot(this);
    loader.setController(this);
    loader.load();
  }

  @Override public void update(Scene scene) {
    // TODO: it might be better to always just rebuild the whole table.
    Collection<PlayerData> missing = new HashSet<>(entityTable.getItems());
    for (Entity entity : scene.getActors()) {
      if (entity instanceof PlayerEntity) {
        PlayerData data = new PlayerData((PlayerEntity) entity, scene);
        if (!entityTable.getItems().contains(data)) {
          entityTable.getItems().add(data);
        }
        missing.remove(data);
      }
    }
    entityTable.getItems().removeAll(missing);
  }

  @Override public Tab getTab() {
    return this;
  }

  private void updatePlayer(PlayerEntity player) {
    playerModel.getSelectionModel().select(player.model);
    skin.setText(player.skin);
    direction.set(player.yaw);
    headYaw.set(player.headYaw);
    headPitch.set(player.pitch);
    leftArmPose.set(player.leftArmPose);
    rightArmPose.set(player.rightArmPose);
    leftLegPose.set(player.leftLegPose);
    rightLegPose.set(player.rightLegPose);
    scale.set(player.scale);
  }

  @Override public void initialize(URL location, ResourceBundle resources) {
    add.setTooltip(new Tooltip("Add a player at the target position."));
    add.setOnAction(e -> {
      Collection<Entity> entities = scene.getActors();
      Set<String> ids = new HashSet<>();
      for (Entity entity : entities) {
        if (entity instanceof PlayerEntity) {
          ids.add(((PlayerEntity) entity).uuid);
        }
      }
      // Pick a new UUID for the new entity.
      long id = System.currentTimeMillis();
      while (ids.contains(String.format("%016X%016X", 0, id))) {
        id += 1;
      }
      Vector3 position = scene.getTargetPosition();
      if (position == null) {
        position = new Vector3(scene.camera().getPosition());
      }
      PlayerEntity player = new PlayerEntity(String.format("%016X%016X", 0, id), position, 0, 0);
      withSelected(selected -> {
        player.skin = selected.skin;
        player.model = selected.model;
      });
      player.randomPoseAndLook();
      scene.addPlayer(player);
      PlayerData data = new PlayerData(player, scene);
      entityTable.getItems().add(data);
      entityTable.getSelectionModel().select(data);
    });
    delete.setTooltip(new Tooltip("Delete the selected player."));
    delete.setOnAction(e -> withSelected(selected -> {
      scene.removePlayer(selected);
      update(scene);
    }));
    selectSkin.setOnAction(e -> withSelected(player -> {
      FileChooser fileChooser = new FileChooser();
      fileChooser.setTitle("Load Skin");
      fileChooser
          .setSelectedExtensionFilter(new FileChooser.ExtensionFilter("Minecraft skin", "*.png"));
      File skinFile = fileChooser.showOpenDialog(getTabPane().getScene().getWindow());
      if (skinFile != null) {
        player.setTexture(skinFile.getAbsolutePath());
        skin.setText(skinFile.getAbsolutePath());
        scene.rebuildActorBvh();
      }
    }));
    entityTable.setRowFactory(tbl -> {
      TableRow<PlayerData> row = new TableRow<>();
      row.setOnMouseClicked(e -> {
        if (e.getClickCount() == 2 && !row.isEmpty()) {
          e.consume();
          try {
            Poser poser = new Poser(row.getItem());
            poser.show();
          } catch (IOException e1) {
            Log.warn("Could not open player poser window.", e1);
          }
        }
      });
      return row;
    });
    cameraToPlayer.setTooltip(new Tooltip("Move the camera to the selected player position."));
    cameraToPlayer.setOnAction(e -> withSelected(player -> scene.camera().moveToPlayer(player)));
    playerToCamera.setTooltip(new Tooltip("Move the selected player to the camera position."));
    playerToCamera.setOnAction(e -> withSelected(player -> {
      player.position.set(scene.camera().getPosition());
      scene.rebuildActorBvh();
    }));
    playerToTarget.setTooltip(new Tooltip("Move the selected player to the current target."));
    playerToTarget.setOnAction(e -> withSelected(player -> {
      Vector3 target = scene.getTargetPosition();
      if (target != null) {
        player.position.set(target);
        scene.rebuildActorBvh();
      }
    }));
    faceCamera.setTooltip(new Tooltip("Makes the selected player look at the camera."));
    faceCamera.setOnAction(e -> withSelected(player -> {
      player.lookAt(scene.camera().getPosition());
      scene.rebuildActorBvh();
    }));
    faceTarget.setTooltip(new Tooltip("Makes the selected player look at the current view target."));
    faceTarget.setOnAction(e -> withSelected(player -> {
      Vector3 target = scene.getTargetPosition();
      if (target != null) {
        player.lookAt(target);
        scene.rebuildActorBvh();
      }
    }));
    entityTable.getSelectionModel().selectedItemProperty()
        .addListener((observable, oldValue, newValue) -> {
          if (newValue != null) {
            updatePlayer(newValue.entity);
          }
        });
    nameCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().name));
    idCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().toString()));
    playerModel.getItems().addAll(PlayerModel.values());
    playerModel.getSelectionModel().selectedItemProperty().addListener(
        (observable, oldValue, newValue) -> withSelected(player -> {
          player.model = newValue;
          scene.rebuildActorBvh();
        }));
    direction.setName("Direction");
    direction.setRange(-Math.PI, Math.PI);
    direction.onValueChange(value -> withSelected(player -> {
      player.yaw = value;
      scene.rebuildActorBvh();
    }));
    headYaw.setName("Head yaw");
    headYaw.setRange(-QuickMath.HALF_PI, QuickMath.HALF_PI);
    headYaw.onValueChange(value -> withSelected(player -> {
      player.headYaw = value;
      scene.rebuildActorBvh();
    }));
    headPitch.setName("Head pitch");
    headPitch.setRange(-QuickMath.HALF_PI, QuickMath.HALF_PI);
    headPitch.onValueChange(value -> withSelected(player -> {
      player.pitch = value;
      scene.rebuildActorBvh();
    }));
    leftArmPose.setName("Left arm pose");
    leftArmPose.setRange(-Math.PI, Math.PI);
    leftArmPose.onValueChange(value -> withSelected(player -> {
      player.leftArmPose = value;
      scene.rebuildActorBvh();
    }));
    rightArmPose.setName("Right arm pose");
    rightArmPose.setRange(-Math.PI, Math.PI);
    rightArmPose.onValueChange(value -> withSelected(player -> {
      player.rightArmPose = value;
      scene.rebuildActorBvh();
    }));
    leftLegPose.setName("Left leg pose");
    leftLegPose.setRange(-QuickMath.HALF_PI, QuickMath.HALF_PI);
    leftLegPose.onValueChange(value -> withSelected(player -> {
      player.leftLegPose = value;
      scene.rebuildActorBvh();
    }));
    rightLegPose.setName("Right leg pose");
    rightLegPose.setRange(-QuickMath.HALF_PI, QuickMath.HALF_PI);
    rightLegPose.onValueChange(value -> withSelected(player -> {
      player.rightLegPose = value;
      scene.rebuildActorBvh();
    }));
    scale.setName("Scale");
    scale.setRange(0.1, 10);
    scale.onValueChange(value -> withSelected(player -> {
      player.scale = value;
      scene.rebuildActorBvh();
    }));
  }

  private void withSelected(Consumer<PlayerEntity> consumer) {
    PlayerData player = entityTable.getSelectionModel().getSelectedItem();
    if (player != null) {
      consumer.accept(player.entity);
    }
  }

  @Override public void setController(RenderControlsFxController controller) {
    scene = controller.getRenderController().getSceneManager().getScene();
  }
}
