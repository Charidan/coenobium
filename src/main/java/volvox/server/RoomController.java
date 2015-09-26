package volvox.server;

import java.util.List;

import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ModelAndView;
import volvox.beans.Room;
import volvox.beans.User;
import volvox.repository.RoomRepository;
import volvox.repository.EntryRepository;
import volvox.repository.UserRepository;

/**
 * This controller is responsible for CRUD of roomRepository including un/seating humans and bots. It is *not*
 * responsible for messaging at the table nor journaling messages for offline humans
 */
@RestController
public class RoomController {
    @Autowired
    EntryRepository entryRepository;
    @Autowired
    RoomRepository roomRepository;
    @Autowired
    RoomService roomService;
    @Autowired
    UserRepository userRepository;

    // TODO load KINDS from data
    public static List<String> KINDS = Lists.newArrayList("chat", "lobby");


    @RequestMapping("/room/list")
    public ModelAndView showTables() {
        Room room = new Room();
        room.setName("New Room Name");
        room.setCode("mainTestCode");
        ModelAndView mav = lobbyMAV(room);
        return mav;
    }

    @RequestMapping(value = "/room/add", method = RequestMethod.POST)
    public ModelAndView addTable(@ModelAttribute Room gameTable) {
        if (gameTable == null || gameTable.getName() == null || gameTable.getName().length() < 1) {
            return roomError("create", "roomname");
        }
        List<Room> old = roomRepository.findByName(gameTable.getName());
        // TODO allow same name in different lobby
        if (old.size() > 0) return roomError("create", "roomexists");

        Room table = new Room();
        table.setName(gameTable.getName());
        table.setCode(gameTable.getCode());
        table.setLobbyId(-1L);
        roomRepository.save(table);
        return new ModelAndView("redirect:/room/list");
    }

    @RequestMapping(value = "/room/delete/{id}", method = RequestMethod.POST)
    public ModelAndView deleteTable(@PathVariable(value="id") String idstr) {
        long id = Long.parseLong(idstr);
        List<Room> victim = roomRepository.findById(id);
        if(victim.size() > 0) roomRepository.delete(victim.get(0));
        else return roomError("delete", "roomdne");
        return new ModelAndView("redirect:/room/list");
    }

    @RequestMapping(value = "/room/move/{newRoomId}/{oldRoomId}/{playerId}")
    public ModelAndView enterRoom(@PathVariable(value="newRoomId") String newRoomIsStr,
                                  @PathVariable(value="oldRoomId") String oldRoomIdStr,
                                  @PathVariable(value="playerId") String playerIdStr) {
        long oldRoomId = Long.parseLong(oldRoomIdStr);
        long newRoomId = Long.parseLong(newRoomIsStr);
        long playerId = Long.parseLong(playerIdStr);
        roomService.exitRoom(oldRoomId, playerId);
        roomService.enterRoom(newRoomId, playerId, true);
        return new ModelAndView("redirect:/room/view/"+newRoomId);
    }

    @RequestMapping(value = "/room/view/{id}")
    public ModelAndView roomView(@PathVariable(value = "id") Long id) {
        Room room = (id == -1) ? mainLobby() : roomRepository.findById(id).get(0);
        List<User> users = roomService.findUsersByRoom(id, false);
        ModelAndView mav = new ModelAndView(room.getCode());
        mav.addObject("room", room);
        mav.addObject("users", users);
        return mav;
    }

    //TODO make roomService.getRoom( ) and move this there
    //fake object for main lobby not really in the database, use id:-1 to avoid autoId collision
    private Room mainLobby() {
        Room lobby = new Room();
        lobby.setCode("lobby");
        lobby.setId(-1);
        lobby.setName("Volvox");
        // the main lobby doesn't have a real parent
        lobby.setLobbyId(-2);
        return lobby;
    }

    private ModelAndView lobbyMAV(Room room) {
        ModelAndView mav = new ModelAndView("lobby");
        mav.addObject("rooms", roomRepository.findAll());
        mav.addObject("gameRoom", room);

        String name = getUsername();
        User user = userRepository.findUserByName(name);
        if (user == null) {
            user = new User();
            user.setName(name);
            user.setPassword(name);
            // TODO replace built in user class
            user.setClazz("Builtinuser class placeholder");
            user = userRepository.save(user);
        }

        mav.addObject("name", name);
        mav.addObject("isadmin", isAdmin(name));
        mav.addObject("userId", ""+user.getId());
        mav.addObject("kinds", KINDS);

        roomService.enterRoom(-1L, user.getId(), true);
        List<User> users = roomService.findUsersByRoom(-1L, false);
        mav.addObject("users", users);

        return mav;
    }

    private ModelAndView roomError(String hdr, String msg) {
        ModelAndView error = new ModelAndView("roomError");
        error.addObject("header", hdr);
        error.addObject("message", msg);
        return error;
    }

    // TODO make this a public method in some kind of util place
    private String getUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName(); //get logged in username
    }

    // TODO make this a property of the lobby room
    private boolean isAdmin(String name) {
        return "admin".equals(name);
    }
}
