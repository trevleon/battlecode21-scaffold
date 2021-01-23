package qualificationbot;

import battlecode.common.*;
import java.util.*;

public class Communication {

    // ID Storing

    static final int MAX_ID = 4096;

    static final int MAX_NUM_FRIENDLY_MUCKRAKERS = 128;
    static int[] friendlyMuckrakerIDs = new int[MAX_NUM_FRIENDLY_MUCKRAKERS];
    static boolean[] friendlyMuckrakerAdded = new boolean[MAX_ID];

    static final int MAX_NUM_FRIENDLY_ECS = 20;
    static int[] friendlyECIDs = new int[MAX_NUM_FRIENDLY_ECS];
    static int friendlyECIdx = 0;

    // called by ec to update id lists
    public static void ecUpdateIDList() {
        Team friendlyTeam = RobotPlayer.rc.getTeam();
        RobotInfo[] sensedRobots = RobotPlayer.rc.senseNearbyRobots();
        int nextIdx = 0;
        for (int i = sensedRobots.length - 1; i >= 0; --i) {
            int id = sensedRobots[i].getID();
            if (sensedRobots[i].getTeam() == friendlyTeam &&
                sensedRobots[i].getType() == RobotType.MUCKRAKER &&
                !friendlyMuckrakerAdded[id % MAX_ID]) {
                while (nextIdx < MAX_NUM_FRIENDLY_MUCKRAKERS && RobotPlayer.rc.canGetFlag(friendlyMuckrakerIDs[nextIdx])) {
                    nextIdx++;
                }
                if (nextIdx != MAX_NUM_FRIENDLY_MUCKRAKERS) break;

                friendlyMuckrakerAdded[id % MAX_ID] = true;
                friendlyMuckrakerIDs[nextIdx++] = id;
            }
        }
    }

    public static void updateIDList() {
        Team friendlyTeam = RobotPlayer.rc.getTeam();
        RobotInfo[] sensedRobots = RobotPlayer.rc.senseNearbyRobots();
        for (int i = sensedRobots.length - 1; i >= 0; --i) {
            int id = sensedRobots[i].getID();
            if (sensedRobots[i].getTeam() == friendlyTeam &&
                sensedRobots[i].getType() == RobotType.ENLIGHTENMENT_CENTER) {
                friendlyECIDs[friendlyECIdx++] = id;
            }
        }
    }

    // Section Robot Info

    static final int MAP_SIZE = 128;
    static final int MAX_NUM_SIEGE_LOCATIONS = 12;

    static final int EC_INFLUENCE_SCALE = 50;
    static final int MAX_EC_INFLUENCE_STORED = EC_INFLUENCE_SCALE * ((1 << 7) - 1);

    static final int EC_TYPE_UNKNOWN = 0;
    static final int EC_TYPE_TAKEN = 1; // could be friendly or surrounded
    static final int EC_TYPE_NEUTRAL = 2;
    static final int EC_TYPE_ENEMY = 3;

    static MapLocation[] siegeLocations = new MapLocation[MAX_NUM_SIEGE_LOCATIONS];
    static int siegeLocationsIdx = 0;
    static int[][] siegeableECAtLocation = new int[MAP_SIZE][MAP_SIZE];
    static int[][] ecInfluence = new int[MAP_SIZE][MAP_SIZE];

    static final int ENEMY_TYPE_UNKNOWN = 0;
    static final int ENEMY_TYPE_MUCKRAKER = 1;
    static final int ENEMY_TYPE_POLITICIAN = 2;
    static final int ENEMY_TYPE_SLANDERER = 3;

    static final int NUM_ENEMY_UNIT_TYPES = 4;

    static int[] closestEnemyDist = new int[NUM_ENEMY_UNIT_TYPES];
    static MapLocation[] closestEnemyLoc = new MapLocation[NUM_ENEMY_UNIT_TYPES];

    // called by ec, uses muckraker flags to get map info
    public static void ecUpdateMapInfo() throws GameActionException {
        // reset closest enemy locations
        // TODO consider retaining the distances for a short while
        Arrays.fill(closestEnemyDist, Integer.MAX_VALUE);
        Arrays.fill(closestEnemyLoc, null);

        MapLocation curLoc = RobotPlayer.rc.getLocation();
        for (int i = MAX_NUM_FRIENDLY_MUCKRAKERS - 1; i >= 0; i--) {
            if (friendlyMuckrakerIDs[i] != 0 && RobotPlayer.rc.canGetFlag(friendlyMuckrakerIDs[i])) {
                int flag = RobotPlayer.rc.getFlag(friendlyMuckrakerIDs[i]);
                int locNum              = flag & 0x3FFF;    // first 14 bits
                int isEC                = flag >> 14 & 0x1; // next 1 bit
                int type                = flag >> 15 & 0x3; // next 2 bits
                int ecInfluenceInfo     = flag >> 17;       // last 7 bits
                int moddedX = locNum % MAP_SIZE;
                int moddedY = locNum / MAP_SIZE;
                MapLocation loc = getLocationFromModded(moddedX, moddedY);
                if (isEC == 1) {
                    if (siegeableECAtLocation[moddedX][moddedY] == EC_TYPE_UNKNOWN) {
                        siegeLocations[siegeLocationsIdx++] = loc;
                    }
                    siegeableECAtLocation[moddedX][moddedY] = type;
                    ecInfluence[moddedX][moddedY] = ecInfluenceInfo * EC_INFLUENCE_SCALE;
                } else if (curLoc.isWithinDistanceSquared(loc, closestEnemyDist[type] - 1)) {
                    closestEnemyLoc[type] = loc;
                    closestEnemyDist[type] = curLoc.distanceSquaredTo(loc);
                }
            }
        }
    }

    // called by muckraker to send info back to ec
    public static void sendMapInfo() throws GameActionException {
        MapLocation curLoc = RobotPlayer.rc.getLocation();
        Team friendlyTeam = RobotPlayer.rc.getTeam();

        RobotInfo closestSiegeableUnit = null;
        RobotInfo closestNonSiegeableUnit = null;
        RobotInfo closestEnemyUnit = null;
        int closestSiegeableUnitDist = Integer.MAX_VALUE;
        int closestNonSiegeableUnitDist = Integer.MAX_VALUE;
        int closestEnemyUnitDist = Integer.MAX_VALUE;
        RobotInfo[] sensedRobots = RobotPlayer.rc.senseNearbyRobots();
        for (int i = sensedRobots.length - 1; i >= 0; --i) {
            Team team = sensedRobots[i].getTeam();
            RobotType type = sensedRobots[i].getType();
            MapLocation robotLoc = sensedRobots[i].getLocation();
            if (team == friendlyTeam && type != RobotType.ENLIGHTENMENT_CENTER) continue;

            if (type != RobotType.ENLIGHTENMENT_CENTER) {
                if (curLoc.isWithinDistanceSquared(robotLoc, closestEnemyUnitDist - 1)) {
                    closestEnemyUnit = sensedRobots[i];
                    closestEnemyUnitDist = curLoc.distanceSquaredTo(robotLoc);
                }
            } else {
                if (team == friendlyTeam || Pathfinding3.getOpenAdjacentLoc(robotLoc) == null) {
                    if (curLoc.isWithinDistanceSquared(robotLoc, closestNonSiegeableUnitDist - 1)) {
                        closestNonSiegeableUnit = sensedRobots[i];
                        closestNonSiegeableUnitDist = curLoc.distanceSquaredTo(robotLoc);
                    }
                } else {
                    if (curLoc.isWithinDistanceSquared(robotLoc, closestSiegeableUnitDist - 1)) {
                        closestSiegeableUnit = sensedRobots[i];
                        closestSiegeableUnitDist = curLoc.distanceSquaredTo(robotLoc);
                    }
                }
            }
        }

        MapLocation targetLoc = null;
        int isEC = 0;
        int type = 0;
        int ecInfluenceInfo = 0;
        if (closestSiegeableUnit != null) {
            targetLoc = closestSiegeableUnit.getLocation();
            isEC = 1;
            type = closestSiegeableUnit.getTeam() == Team.NEUTRAL ? EC_TYPE_NEUTRAL : EC_TYPE_ENEMY;
            ecInfluenceInfo = (Math.min(closestSiegeableUnit.getInfluence(), MAX_EC_INFLUENCE_STORED) - 1) / EC_INFLUENCE_SCALE + 1;
        } else if (closestEnemyUnit != null) {
            targetLoc = closestEnemyUnit.getLocation();
            isEC = 0;
            switch (closestEnemyUnit.getType()) {
                case MUCKRAKER:
                    type = ENEMY_TYPE_MUCKRAKER;
                    break;
                case POLITICIAN:
                    type = ENEMY_TYPE_POLITICIAN;
                    break;
                case SLANDERER:
                    type = ENEMY_TYPE_SLANDERER;
                    break;
                default:
                    type = ENEMY_TYPE_UNKNOWN;
                    break;
            }
        } else if (closestNonSiegeableUnit != null) {
            targetLoc = closestNonSiegeableUnit.getLocation();
            isEC = 0;
            type = EC_TYPE_TAKEN;
        } else {
            targetLoc = curLoc; // just so it's not null
            isEC = 0;
            type = EC_TYPE_UNKNOWN;
        }

        int moddedX = targetLoc.x % MAP_SIZE;
        int moddedY = targetLoc.y % MAP_SIZE;
        int locNum = moddedX | (moddedY << 7); // first 14 bits

        RobotPlayer.rc.setFlag(locNum |
                              (isEC << 14) |
                              (type << 15) |
                              (ecInfluenceInfo  << 17));
    }

    // Section Mission Info
    
    static final int NUM_MISSION_TYPES = 4;

    // mission types
    static final int MISSION_TYPE_SLEUTH = 0;
    static final int MISSION_TYPE_SIEGE = 1;
    static final int MISSION_TYPE_DEMUCK = 2;
    static final int MISSION_TYPE_DEPOLI = 3;

    static final int NO_MISSION_AVAILABLE = 1 << 14;

    static MapLocation[][] latestMissionSectionLoc = new MapLocation[MAX_NUM_FRIENDLY_ECS][NUM_MISSION_TYPES];

    // called by any non-ec unit, uses ec flags to get mission info
    public static void updateSectionMissionInfo() throws GameActionException {
        for (int i = 0; i < MAX_NUM_FRIENDLY_ECS; i++) {
            if (RobotPlayer.rc.canGetFlag(friendlyECIDs[i])) {
                int flag = RobotPlayer.rc.getFlag(friendlyECIDs[i]);
                int missionType         = flag & 0x3;   // first 2 bits (only 2 used)
                int missionLocNum       = flag >> 2;    // last 22 bits (only 14 used unless sending a no mission available message)
                latestMissionSectionLoc[i][missionType] = null;
                if (missionLocNum != NO_MISSION_AVAILABLE) {
                    latestMissionSectionLoc[i][missionType] = new MapLocation(missionLocNum % MAP_SIZE,
                                                                              missionLocNum / MAP_SIZE);
                }
            }
        }
    }

    public static void sendMissionInfo(MapLocation missionLoc, int missionType) throws GameActionException {
        int missionLocNum = missionLoc == null ? NO_MISSION_AVAILABLE : (missionLoc.x % MAP_SIZE) | ((missionLoc.y % MAP_SIZE) << 7);
        RobotPlayer.rc.setFlag(missionType | (missionLocNum << 2));
    }

    public static MapLocation getClosestMissionOfType(int missionType) {
        MapLocation curLoc = RobotPlayer.rc.getLocation();
        MapLocation closestMissionLoc = null;
        int closestMissionDist = Integer.MAX_VALUE;
        for (int i = 0; i < MAX_NUM_FRIENDLY_ECS; i++) {
            if (friendlyECIDs[i] != 0 &&
                RobotPlayer.rc.canGetFlag(friendlyECIDs[i]) &&
                latestMissionSectionLoc[i][missionType] != null &&
                curLoc.isWithinDistanceSquared(latestMissionSectionLoc[i][missionType], closestMissionDist - 1)) {
                    closestMissionLoc = latestMissionSectionLoc[i][missionType];
                    closestMissionDist = curLoc.distanceSquaredTo(closestMissionLoc);
            }
        }
        return closestMissionLoc;
    }

    // Utilities

    private static MapLocation getLocationFromModded(int moddedX, int moddedY) {
        MapLocation curLoc = RobotPlayer.rc.getLocation();

        int curXModded = curLoc.x % MAP_SIZE;
        int curYModded = curLoc.y % MAP_SIZE;

        int targetX;
        int xDiff = (moddedX - curXModded + MAP_SIZE) % MAP_SIZE;
        if (xDiff < MAP_SIZE / 2) {
            targetX = curLoc.x + xDiff;
        } else {
            targetX = curLoc.x - (MAP_SIZE - xDiff);
        }

        int targetY;
        int yDiff = (moddedY - curYModded + MAP_SIZE) % MAP_SIZE;
        if (yDiff < MAP_SIZE / 2) {
            targetY = curLoc.y + yDiff;
        } else {
            targetY = curLoc.y - (MAP_SIZE - yDiff);
        }

        return new MapLocation(targetX, targetY);
    }
}

