package automail;

import exceptions.ExcessiveDeliveryException;
import exceptions.ItemTooHeavyException;
import strategies.IMailPool;
import java.util.Map;
import java.util.TreeMap;

/**
 * The robot delivers mail!
 */
public class Robot implements IMovable{

    public static final int INDIVIDUAL_MAX_WEIGHT = 2000;
    public static final int PAIR_MAX_WEIGHT = 2600;
    public static final int TRIPLE_MAX_WEIGHT = 3000;

    IMailDelivery delivery;
    protected final String id;
    /** Possible states the robot can be in */
    public enum RobotState { DELIVERING, WAITING, RETURNING }
    public RobotState current_state;
    private int current_floor;
    private int destination_floor;
    private IMailPool mailPool;
    private boolean receivedDispatch;
    
    private MailItem deliveryItem = null;
    private MailItem tube = null;
    
    private int deliveryCounter;
    

    /**
     * Initiates the robot's location at the start to be at the mailroom
     * also set it to be waiting for mail.
     * @param behaviour governs selection of mail items for delivery and behaviour on priority arrivals
     * @param delivery governs the final delivery
     * @param mailPool is the source of mail items
     */
    public Robot(IMailDelivery delivery, IMailPool mailPool){
    	id = "R" + hashCode();
        // currentState = RobotState.WAITING;
    	current_state = RobotState.RETURNING;
        current_floor = Building.MAILROOM_LOCATION;
        this.delivery = delivery;
        this.mailPool = mailPool;
        this.receivedDispatch = false;
        this.deliveryCounter = 0;
    }
    
    /**
     * This method set a robot's receivedDispatch boolean value to true
     */
    public void dispatch() {
    	receivedDispatch = true;
    }

    /**
     * This is called on every time step
     * @throws ExcessiveDeliveryException if robot delivers more than the capacity of the tube without refilling
     */
    @Override
    public void step() {
        try {
            switch(current_state) {
                /** This state is triggered when the robot is returning to the mailroom after a delivery */
                case RETURNING:
                    /** If its current position is at the mailroom, then the robot should change state */
                    if(current_floor == Building.MAILROOM_LOCATION){
                        if (tube != null) {
                            mailPool.addToPool(tube);
                            System.out.printf("T: %3d > old addToPool [%s]%n", Clock.Time(), tube.toString());
                            tube = null;
                        }
                        /** Tell the sorter the robot is ready */
                        mailPool.registerWaiting(this);
                        changeState(RobotState.WAITING);
                    } else {
                        /** If the robot is not at the mailroom floor yet, then move towards it! */
                        moveTowards(Building.MAILROOM_LOCATION);
                        break;
                    }
                case WAITING:
                    /** If the StorageTube is ready and the Robot is waiting in the mailroom then start the delivery */
                    if(!isEmpty() && receivedDispatch){
                        receivedDispatch = false;
                        deliveryCounter = 0; // reset delivery counter
                        setRoute();
                        changeState(RobotState.DELIVERING);
                    }
                    break;
                case DELIVERING:
                    if(current_floor == destination_floor){ // If already here drop off either way

                        if (mailPool.getRobotsDelivering(deliveryItem) == TEAM_SIZE.ONE.getValue()){
                            /**
                             * Last robot to deliver this item(as an individual or as a team),
                             * report this to the simulator!
                             */
                            delivery.deliver(deliveryItem);
                        } else {
                            mailPool.removeRobotFromDelivery(deliveryItem);
                        }
                        deliveryItem = null;
                        deliveryCounter++;
                        if(deliveryCounter > 2){  // Implies a simulation bug
                            throw new ExcessiveDeliveryException();
                        }
                        /** Check if want to return, i.e. if there is no item in the tube*/
                        if(tube == null){
                            changeState(RobotState.RETURNING);
                        }
                        else{
                            /** If there is another item, set the robot's route to the location to deliver the item */
                            deliveryItem = tube;
                            tube = null;
                            setRoute();
                            changeState(RobotState.DELIVERING);
                        }


                    } else {
                        /** The robot is not at the destination yet, move towards it! */
                        moveTowards(destination_floor);
                    }
                    break;
            }
        }
        catch (ExcessiveDeliveryException e){
            e.printStackTrace();
        }
    }

    /**
     * Sets the route for the robot
     */
    private void setRoute() {
        /** Set the destination floor */
        destination_floor = deliveryItem.getDestFloor();
    }

    /**
     * Generic function that moves the robot towards the destination
     * @param destination the floor towards which the robot is moving
     */
    private void moveTowards(int destination) {
        if(current_floor < destination){
            current_floor++;
        } else {
            current_floor--;
        }
    }
    
    private String getIdTube() {
    	return String.format("%s(%1d)", id, (tube == null ? 0 : 1));
    }
    
    /**
     * Prints out the change in state
     * @param nextState the state to which the robot is transitioning
     */
    private void changeState(RobotState nextState){
    	assert(!(deliveryItem == null && tube != null));
    	if (current_state != nextState) {
    		// Example: R(1) means tube is also filled before delivery
            System.out.printf("T: %3d > %7s changed from %s to %s%n", Clock.Time(), getIdTube(), current_state, nextState);
    	}
    	current_state = nextState;
    	if(nextState == RobotState.DELIVERING){
            System.out.printf("T: %3d > %7s-> [%s]%n", Clock.Time(), getIdTube(), deliveryItem.toString());
    	}
    }

	public MailItem getTube() {
		return tube;
	}
    
	static private int count = 0;
	static private Map<Integer, Integer> hashMap = new TreeMap<Integer, Integer>();

	@Override
	public int hashCode() {
		Integer hash0 = super.hashCode();
		Integer hash = hashMap.get(hash0);
		if (hash == null) { hash = count++; hashMap.put(hash0, hash); }
		return hash;
	}

	public boolean isEmpty() {
		return (deliveryItem == null && tube == null);
	}

    /**
     * This method adds a mail item to the hands of a robot
     * if the hands are empty
     * @param mailItem A mail item to be added to the hands
     * @throws ItemTooHeavyException
     */
	public void addToHand(MailItem mailItem) throws ItemTooHeavyException {
		assert(deliveryItem == null);
		deliveryItem = mailItem;
		// Adjust the max. weight a robot's hand can carry
        // based on the team capacity and number of robots in the mail pool
		if (deliveryItem.weight > mailPool.getSysMaxWeight()) throw new ItemTooHeavyException();
	}

    /**
     * This method adds a mail item to the tube of a robot
     * if the tube is empty
     * @param mailItem A mail item to be added to the tube
     * @throws ItemTooHeavyException
     */
	public void addToTube(MailItem mailItem) throws ItemTooHeavyException {
		assert(tube == null);
		tube = mailItem;
		if (tube.weight > INDIVIDUAL_MAX_WEIGHT) throw new ItemTooHeavyException();
	}

}
