package sprintbot;
import battlecode.common.*;

public class Slanderer {
    static final int MAX_SQUARED_DIST_FROM_START = 30;

    static RobotController rc;
    static MapLocation startLoc;

    static MapLocation missionSectionLoc;
    static int missionType;
    
    public static void run() throws GameActionException {
        int turn = 0;
        rc = RobotPlayer.rc;
        initialize();
        while (true) {
            if (rc.getType() == RobotType.POLITICIAN) {
                Pathfinding3.moveTo(startLoc);
                if (rc.getLocation().equals(startLoc) && rc.canEmpower(2)) {
                    rc.empower(2);
                }
            }
            Communication.updateIDList(false);
            Communication.updateSectionMissionInfo();
            if (missionType == Communication.MISSION_TYPE_UNKNOWN) {
                missionSectionLoc = Communication.getClosestMission();
                if (missionSectionLoc != null) {
                    missionType = Communication.sectionMissionInfo[missionSectionLoc.x][missionSectionLoc.y];
                }
            }
            executeTurn(turn++);
            Clock.yield();
        }
    }

    public static void initialize() {
        startLoc = rc.getLocation();
    }
    
    public static void executeTurn(int turnNumber) throws GameActionException {
        MapLocation targetLoc = missionSectionLoc != null ? Communication.getSectionCenterLoc(missionSectionLoc) : null;

        switch (missionType) {
            case Communication.MISSION_TYPE_HIDE:
                hideAtLocation(targetLoc);
                break;
            default:
                roamCloseToStart();
                break;
        }
    }

    private static void roamCloseToStart() throws GameActionException {
        Direction[] allDirections = Direction.allDirections();
        for (int i = allDirections.length - 1; i >= 0; i--) {
            Direction nextDir = allDirections[i];
            MapLocation nextLoc = rc.getLocation().add(nextDir);
            if (nextLoc.isWithinDistanceSquared(startLoc, MAX_SQUARED_DIST_FROM_START) && rc.canMove(nextDir)) {
                rc.move(nextDir);
            }
        }
    }

    public static void hideAtLocation(MapLocation loc) throws GameActionException  {
        if(!loc.equals(rc.getLocation())){
            Pathfinding3.moveTo(loc);
        }
    }
}
