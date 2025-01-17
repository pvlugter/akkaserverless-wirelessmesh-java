 package wirelessmesh.domain;

 import com.google.protobuf.Empty;
 import com.akkaserverless.javasdk.EntityId;
 import com.akkaserverless.javasdk.eventsourcedentity.*;

 import wirelessmesh.DeviceService;
 import wirelessmesh.LifxDeviceService;
 import wirelessmesh.NoopPubsubService;
 import wirelessmesh.PubsubService;
 import wirelessmeshdomain.Wirelessmeshdomain.*;
 import wirelessmeshservice.Wirelessmeshservice.*;

 import java.io.IOException;
 import java.util.ArrayList;
 import java.util.List;
 import java.util.Optional;
 import java.util.stream.Collectors;
 import java.util.stream.Stream;

 /**
  * A customer location entity.
  *
  * As an entity, I will be seeded with my current state upon loading and thereafter will completely serve the
  * backend needs for a particular device. There is no practical limit to how many of these entities you can have,
  * across the application cluster. Look at each instance of this entity as being roughly equivalent to a row in a
  * database, only each one is completely addressable and in memory.
  *
  * Event sourcing was selected in order to have complete traceability into the behavior of devices for the purposes
  * of security, analytics and simulation.
  */
 @EventSourcedEntity(entityType = "CustomerLocationEntity")
 public class CustomerLocationEntity {

     private PubsubService pubsubService = new NoopPubsubService();
     private DeviceService deviceService = new LifxDeviceService();

     /**
      * This section contains the private state variables necessary for this entity.
      */

     private String customerLocationId;

     private boolean added = false;

     private boolean removed = false;

     private String accessToken = "";

     private List<Device> devices = new ArrayList<Device>();

     /**
      * Constructor.
      * @param customerLocationId The entity id will be the customerLocationId, the unique key for this entity.
      */
     public CustomerLocationEntity(@EntityId String customerLocationId) {
         this.customerLocationId = customerLocationId;
     }

     /**
      * This is the command handler for adding a customer location as defined in protobuf.
      * @param addCustomerLocationCommand the command message from protobuf
      * @param ctx the application context
      * @return Empty (unused)
      */
     @CommandHandler
     public Empty addCustomerLocation(AddCustomerLocationCommand addCustomerLocationCommand, CommandContext ctx) {
         if (added) {
             ctx.fail("Customer location already added");
         }
         else if (!isAlphaNumeric(addCustomerLocationCommand.getCustomerLocationId())) {
             ctx.fail("Customer location id must be alphanumeric");
         }
         else if (!isAlphaNumeric(addCustomerLocationCommand.getAccessToken())) {
             ctx.fail("Access token must be alphanumeric");
         }
         else {
             CustomerLocationAdded event = CustomerLocationAdded.newBuilder()
                     .setCustomerLocationId(addCustomerLocationCommand.getCustomerLocationId())
                     .setAccessToken(addCustomerLocationCommand.getAccessToken())
                     .build();

             ctx.emit(event);
             pubsubService.publish(event.toByteString());
         }

         return Empty.getDefaultInstance();
     }

     /**
      * This is the event handler for adding a customer location. It is here we update current state due to
      * successful storage to the eventlog.
      *
      * @param customerLocationAdded the event previously emitted in the command handler, now safely stored.
      */
     @EventHandler
     public void customerLocationAdded(CustomerLocationAdded customerLocationAdded) {
         this.added = true;
         this.removed = false;
         this.accessToken = customerLocationAdded.getAccessToken();
     }

     /**
      * This is the command handler for removing a customer location as defined in protobuf.
      * @param removeCustomerLocationCommand the command message from protobuf
      * @param ctx the application context
      * @return Empty (unused)
      */
     @CommandHandler
     public Empty removeCustomerLocation(RemoveCustomerLocationCommand removeCustomerLocationCommand, CommandContext ctx) {
         if (!added) {
             ctx.fail("Customer location does not exist");
         }
         else if (removed) {
             ctx.fail("Customer location already removed");
         }
         else {
             CustomerLocationRemoved event = CustomerLocationRemoved.newBuilder()
                     .setCustomerLocationId(removeCustomerLocationCommand.getCustomerLocationId())
                     .build();

             ctx.emit(event);
             pubsubService.publish(event.toByteString());
         }

         return Empty.getDefaultInstance();
     }

     /**
      * This is the event handler for removing a customer location. It is here we update current state due to
      * successful storage to the eventlog.
      *
      * @param customerLocationRemoved the event previously emitted in the command handler, now safely stored.
      */
     @EventHandler
     public void customerLocationRemoved(CustomerLocationRemoved customerLocationRemoved) {
         this.removed = true;
         this.added = false;
         devices = new ArrayList<Device>();
     }

     /**
      * This is the command handler for activating a wirelessmesh device as defined in protobuf.
      * @param activateDeviceCommand the command message from protobuf
      * @param ctx the application context
      * @return Empty (unused)
      */
     @CommandHandler
     public Empty activateDevice(ActivateDeviceCommand activateDeviceCommand, CommandContext ctx) {
         if (removed) {
             ctx.fail("customerLocation does not exist.");
         }
         else if (findDevice(activateDeviceCommand.getDeviceId()).isPresent()) {
             ctx.fail("Device already activated");
         }
         else if (!isAlphaNumeric(activateDeviceCommand.getDeviceId())) {
             ctx.fail("Device id must be alphanumeric");
         }
         else {
             DeviceActivated event = DeviceActivated.newBuilder()
                     .setDeviceId(activateDeviceCommand.getDeviceId())
                     .setCustomerLocationId(activateDeviceCommand.getCustomerLocationId())
                     .build();

             ctx.emit(event);
             pubsubService.publish(event.toByteString());
         }

         return Empty.getDefaultInstance();
     }

     /**
      * This is the event handler for activating a wirelessmesh device. It is here we update current state due to
      * successful storage to the eventlog.
      *
      * @param deviceActivated the event previously emitted in the command handler, now safely stored.
      */
     @EventHandler
     public void deviceActivated(DeviceActivated deviceActivated) {
         devices.add(Device.newBuilder()
                 .setDeviceId(deviceActivated.getDeviceId())
                 .setCustomerLocationId(customerLocationId)
                 .setActivated(true)
                 .setNightlightOn(false)
                 .build());
     }

     /**
      * This is the command handler for removing a wirelessmesh device as defined in protobuf.
      * @param removeDeviceCommand the command message from protobuf
      * @param ctx the application context
      * @return Empty (unused)
      */
     @CommandHandler
     public Empty removeDevice(RemoveDeviceCommand removeDeviceCommand, CommandContext ctx) {
         if (!added || removed) {
             ctx.fail("customerLocation does not exist.");
         }
         else if (!findDevice(removeDeviceCommand.getDeviceId()).isPresent()) {
             ctx.fail("Device does not exist");
         }
         else {
             DeviceRemoved event = DeviceRemoved.newBuilder()
                     .setDeviceId(removeDeviceCommand.getDeviceId())
                     .setCustomerLocationId(removeDeviceCommand.getCustomerLocationId()).build();

             ctx.emit(event);
             pubsubService.publish(event.toByteString());
         }

         return Empty.getDefaultInstance();
     }

     /**
      * This is the event handler for removing a wirelessmesh device. It is here we update current state due to
      * successful storage to the eventlog.
      *
      * @param deviceRemoved the event previously emitted in the command handler, now safely stored.
      */
     @EventHandler
     public void deviceRemoved(DeviceRemoved deviceRemoved) {
         devices = devices.stream().filter(d -> !d.getDeviceId().equals(deviceRemoved.getDeviceId()))
                 .collect(Collectors.toList());
     }

     /**
      * This is the command handler for assigning a wirelessmesh device to a room as defined in protobuf.
      * @param assignRoomCommand the command message from protobuf
      * @param ctx the application context
      * @return Empty (unused)
      */
     @CommandHandler
     public Empty assignRoom(AssignRoomCommand assignRoomCommand, CommandContext ctx) {
         if (removed) {
             ctx.fail("customerLocation does not exist.");
         }
         else if (!findDevice(assignRoomCommand.getDeviceId()).isPresent()) {
             ctx.fail("Device does not exist");
         }
         else if (!isAlphaNumeric(assignRoomCommand.getRoom())) {
             ctx.fail("Room must be alphanumeric");
         }
         else {
             RoomAssigned event = RoomAssigned.newBuilder()
                     .setDeviceId(assignRoomCommand.getDeviceId())
                     .setCustomerLocationId(assignRoomCommand.getCustomerLocationId())
                     .setRoom(assignRoomCommand.getRoom()).build();

             ctx.emit(event);
             pubsubService.publish(event.toByteString());
         }

         return Empty.getDefaultInstance();
     }

     /**
      * This is the event handler for assigning a wirelessmesh device to a room. It is here we update current state due to
      * successful storage to the eventlog.
      *
      * @param roomAssigned the event previously emitted in the command handler, now safely stored.
      */
     @EventHandler
     public void roomAssigned(RoomAssigned roomAssigned) {
         Device old = findDevice(roomAssigned.getDeviceId()).get();
         Device device = old.toBuilder()
                 .setRoom(roomAssigned.getRoom())
                 .build();

         replaceDevice(device);
     }

     /**
      * This is the command handler for toggling the wirelessmesh device nightlight as defined in protobuf.
      * @param toggleNightlightCommand the command message from protobuf
      * @param ctx the application context
      * @return Empty (unused)
      */
     @CommandHandler
     public Empty toggleNightlight(ToggleNightlightCommand toggleNightlightCommand, CommandContext ctx) throws IOException {
         if (removed) {
             ctx.fail("customerLocation does not exist.");
         }
         else {
             Optional<Device> deviceMaybe = findDevice(toggleNightlightCommand.getDeviceId());

             if (!deviceMaybe.isPresent()) {
                 ctx.fail("Device does not exist");
             }
             else {
                 NightlightToggled event = NightlightToggled.newBuilder()
                         .setDeviceId(toggleNightlightCommand.getDeviceId())
                         .setCustomerLocationId(toggleNightlightCommand.getCustomerLocationId())
                         .setNightlightOn(!deviceMaybe.get().getNightlightOn()).build();

                 ctx.emit(event);
                 deviceService.toggleNightlight(accessToken, toggleNightlightCommand.getDeviceId());
                 pubsubService.publish(event.toByteString());
             }
         }

         return Empty.getDefaultInstance();
     }

     /**
      * This is the event handler for toggling the wirelessmesh device nightlight. It is here we update current state due to
      * successful storage to the eventlog.
      *
      * @param nightlightToggled the event previously emitted in the command handler, now safely stored.
      */
     @EventHandler
     public void nightlightToggled(NightlightToggled nightlightToggled) {
         Device old = findDevice(nightlightToggled.getDeviceId()).get();
         Device device = old.toBuilder()
                 .setNightlightOn(nightlightToggled.getNightlightOn())
                 .build();

         replaceDevice(device);
     }

     /**
      * This is the command handler geting the current state of the devices as defined in protobuf.
      * @param GetCustomerLocationCommand the command message from protobuf
      * @param ctx the application context
      * @return Empty (unused)
      */
     @CommandHandler
     public CustomerLocation getCustomerLocation(GetCustomerLocationCommand getCustomerLocationCommand, CommandContext ctx) {
         if (removed || !added) {
             ctx.fail("customerLocation does not exist.");
         }

         return CustomerLocation.newBuilder().setCustomerLocationId(customerLocationId)
                 .setAccessToken(accessToken)
                 .setAdded(added)
                 .setRemoved(removed)
                 .addAllDevices(devices).build();
     }

     /**
      * Helper function to find a device in the device collection.
      */
     private Optional<Device> findDevice(String deviceId) {
         return devices.stream()
                 .filter(d -> d.getDeviceId().equals(deviceId))
                 .findFirst();
     }

     /**
      * Helper function to replace the state of a given device within the device collection.
      */
     private void replaceDevice(Device device) {
         devices = Stream.concat(devices.stream()
                         .filter(d -> !d.getDeviceId().equals(device.getDeviceId())),
                 Stream.of(device))
                 .collect(Collectors.toList());
     }

     private boolean isAlphaNumeric(String id) {
         return id.matches("^[a-zA-Z0-9_-]*$");
     }
 }