package route.route;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;

import route.circuit.Circuit;
import route.circuit.resource.Opin;
import route.circuit.resource.ResourceGraph;
import route.circuit.resource.RouteNode;
import route.circuit.resource.RouteNodeType;

public class ConnectionRouter {
	final ResourceGraph rrg;
	final Circuit circuit;
	
	private float pres_fac;
	private float alpha;
	
	private final PriorityQueue<RouteNode> queue;
	
	private final Collection<RouteNodeData> nodesTouched;
	
	private final List<String> printRouteInformation;
	
	public static final boolean debug = false;
	
	public ConnectionRouter(ResourceGraph rrg, Circuit circuit) {
		this.rrg = rrg;
		this.circuit = circuit;

		this.nodesTouched = new ArrayList<RouteNodeData>();
		
		this.queue = new PriorityQueue<>(Comparators.PRIORITY_COMPARATOR);
		
		this.printRouteInformation = new ArrayList<>();
	}
    
    public int route() {
    	System.out.println("---------------------------");
    	System.out.println("|         HROUTE          |");
    	System.out.println("---------------------------");
    	System.out.println("Num nets: " + this.circuit.getNets().size());
		System.out.println("Num cons: " + this.circuit.getConnections().size());
		System.out.println("---------------------------");
		System.out.println();
	
		this.doRouting("Route all", this.circuit.getConnections(), 100, true, 4, 1.5f);
		
		/***************************
		 * OPIN tester: test if each
		 * net uses only one OPIN
		 ***************************/
		for(Net net : this.circuit.getNets()) {
			Set<Opin> opins = new HashSet<>();
			String name = null;
			for(Connection con : net.getConnections()) {
				Opin opin = con.getOpin();
				if(opin == null) {
					System.out.println("Connection has no opin!");
				} else {
					opins.add(opin);
				}
			}
			if(opins.size() != 1) {
				System.out.println("Net " + name + " has " + opins.size() + " opins");
			} 
		}
		
		return -1;
	}
    private void doRouting(String name, Collection<Connection> connections, int nrOfTrials, boolean routeAll, int fixOpins, float alpha) {
		System.out.printf("----------------------------------------------------------\n");
		System.out.println(name);
		int timeMilliseconds = this.doRouting(connections, nrOfTrials, routeAll, fixOpins, alpha);
		System.out.printf("----------------------------------------------------------\n");
		System.out.println("Runtime " + timeMilliseconds + " ms");
		System.out.printf("----------------------------------------------------------\n\n\n");
		
		this.printRouteInformation.add(String.format("%s %d", name, timeMilliseconds));
    }
    private int doRouting(Collection<Connection> connections, int nrOfTrials, boolean routeAll, int fixOpins, float alpha) {
    	long start = System.nanoTime();
    	
    	this.alpha = alpha;
    	
    	this.nodesTouched.clear();
    	this.queue.clear();
		
    	float initial_pres_fac = 0.6f;
		float pres_fac_mult = 2;//1.3 or 2.0
		float acc_fac = 1.0f;
		this.pres_fac = initial_pres_fac;
		
		int itry = 1;
		
		Map<Connection, Integer> mapOfConnections = new HashMap<>();
		Map<Connection, Integer> sortedMapOfConnections = null;
		boolean bbnotfanout = false;
		if(bbnotfanout) {
			for(Connection con : connections) {
				mapOfConnections.put(con, con.boundingBox);
			}
			BBComparator bvc =  new BBComparator(mapOfConnections);
			
			sortedMapOfConnections = new TreeMap<>(bvc);
	        sortedMapOfConnections.putAll(mapOfConnections);
		} else {
			for(Connection con : connections) {
				mapOfConnections.put(con, con.net.fanout);
			}
			FanoutComparator bvc =  new FanoutComparator(mapOfConnections);
			
			sortedMapOfConnections = new TreeMap<>(bvc);
	        sortedMapOfConnections.putAll(mapOfConnections);
		}
        
        Set<Net> nets = new HashSet<>();
        for(Connection con : connections) {
        	nets.add(con.net);
        }
        List<Net> sortedNets = new ArrayList<>(nets);
        Collections.sort(sortedNets, Comparators.FANOUT);
        
        System.out.printf("---------  -----  ---------  -----------------  -----------\n");
        System.out.printf("%9s  %5s  %9s  %17s  %11s\n", "Iteration", "Alpha", "Time (ms)", "Overused RR Nodes", "Wire-Length");
        System.out.printf("---------  -----  ---------  -----------------  -----------\n");
        
        Set<RouteNode> overUsed = new HashSet<>();
        
        boolean validRouting = false;
        
        while (itry <= nrOfTrials) {
        	validRouting = true;
        	long iterationStart = System.nanoTime();

        	//Fix opins in order of high fanout nets
        	if(itry >= fixOpins) {
            	for(Net net : sortedNets) {
            		if(!net.hasOpin()) {
            			Opin opin = net.getUniqueOpin();
            			if(opin != null) {
            				if(!opin.overUsed()) {
            					net.setOpin(opin);
            				}
            			}
            			validRouting = false;
            		}
            	}
            	for(Net net : sortedNets) {
            		if(!net.hasOpin()) {
            			Opin opin = net.getMostUsedOpin();
                		if(!opin.used()) {
                			net.setOpin(opin);
                		}
                		validRouting = false;
            		}
            	}
        	} else if(fixOpins < Integer.MAX_VALUE){
        		validRouting = false;
        	}
        			
        	for(Connection con : sortedMapOfConnections.keySet()) {
				if((itry == 1 && routeAll) || con.congested()) {
					this.ripup(con);
					this.route(con);
					this.add(con);
					
					validRouting = false;
				} else if(con.net.hasOpin()) {
					if(con.getOpin().index != con.net.getOpin().index) {
						this.ripup(con);
						this.route(con);
						this.add(con);
						
						validRouting = false;
					}
				}
			}
        	
			//Runtime
			long iterationEnd = System.nanoTime();
			int rt = (int) Math.round((iterationEnd-iterationStart) * Math.pow(10, -6));
			
			String numOverUsed = String.format("%8s", "---");
			String overUsePercentage = String.format("%7s", "---");
			String wireLength = String.format("%11s", "---");
			
			if(debug) {
				overUsed.clear();
				for (Connection conn : connections) {
					for (RouteNode node : conn.routeNodes) {
						if (node.overUsed()) {
							overUsed.add(node);
						}
					}
				}
				int numRouteNodes = this.rrg.getRouteNodes().size();
				numOverUsed = String.format("%8d", overUsed.size());
				overUsePercentage = String.format("%6.2f%%", 100.0 * (float)overUsed.size() / numRouteNodes);
				
				wireLength = String.format("%11d", this.rrg.congestedTotalWireLengt());
			}
			
			
			//Check if the routing is realizable, if realizable return, the routing succeeded 
			if (validRouting){
				this.circuit.setConRouted(true);
		        
				long end = System.nanoTime();
				int timeMilliSeconds = (int)Math.round((end-start) * Math.pow(10, -6));
				return timeMilliSeconds;
			} else {
				System.out.printf("%9d  %.3f %9d  %s  %s  %s\n", itry, this.alpha, rt, numOverUsed, overUsePercentage, wireLength);
			}
			
			//Updating the cost factors
			if (itry == 1) {
				this.pres_fac = initial_pres_fac;
			} else {
				this.pres_fac *= pres_fac_mult;
			}
			this.updateCost(this.pres_fac, acc_fac);
			
			itry++;
		}
        
		if (itry == nrOfTrials + 1) {
			System.out.println("Routing failled after " + itry + " trials!");
			
			int maxNameLength = 0;
			
			Set<RouteNode> overused = new HashSet<>();
			for (Connection conn: connections) {
				for (RouteNode node: conn.routeNodes) {
					if (node.overUsed()) {
						overused.add(node);
					}
				}
			}
			for (RouteNode node: overused) {
				if (node.overUsed()) {
					if(node.toString().length() > maxNameLength) {
						maxNameLength = node.toString().length();
					}
				}
			}
			
			for (RouteNode node: overused) {
				if (node.overUsed()) {
					System.out.println(node.toString());
				}
			}
			System.out.println();
		}
		
		long end = System.nanoTime();
		int timeMilliSeconds = (int)Math.round((end-start) * Math.pow(10, -6));
		return timeMilliSeconds;
    }

	private void ripup(Connection con) {
		for (RouteNode node : con.routeNodes) {
			RouteNodeData data = node.routeNodeData;
			
			data.removeSource(con.source);
			
			// Calculation of present congestion penalty
			node.updatePresentCongestionPenalty(this.pres_fac);
		}
	}
	private void add(Connection con) {
		for (RouteNode node : con.routeNodes) {
			RouteNodeData data = node.routeNodeData;

			data.addSource(con.source);

			// Calculation of present congestion penalty
			node.updatePresentCongestionPenalty(this.pres_fac);
		}
	}
	private boolean route(Connection con) {
		// Clear Routing
		con.resetConnection();

		// Clear Queue
		this.queue.clear();
		
		// Set target flag sink
		RouteNode sink = con.sinkRouteNode;
		sink.target = true;
		
		// Add source to queue
		RouteNode source = con.sourceRouteNode;
		float source_cost = getRouteNodeCost(source, con);
		this.addNodeToQueue(source, null, source_cost, getLowerBoundTotalPathCost(source, con, source_cost));
		
		// Start Dijkstra / directed search
		while (!targetReached()) {//TODO CHECK
			this.expandFirstNode(con);
		}
		
		// Reset target flag sink
		sink.target = false;
		
		// Save routing in connection class
		this.saveRouting(con);
		
		// Reset path cost from Dijkstra Algorithm
		this.resetPathCost();

		return true;
	}

		
	private void saveRouting(Connection con) {
		RouteNode rn = this.queue.peek();
		while (rn != null) {
			con.addRouteNode(rn);
			rn = rn.routeNodeData.prev;
		}
	}

	private boolean targetReached() {
		if(this.queue.peek()==null){
			System.out.println("queue is empty");			
			return false;
		} else {
			return queue.peek().target;
		}
	}
	
	private void resetPathCost() {
		for (RouteNodeData node : this.nodesTouched) {
			node.resetPathCosts();
		}
		this.nodesTouched.clear();
	}

	private void expandFirstNode(Connection con) {
		if (this.queue.isEmpty()) {
			System.out.println(con.netName + " " + con.source.getPortName() + " " + con.sink.getPortName());
			throw new RuntimeException("Queue is empty: target unreachable?");
		}

		RouteNode node = this.queue.poll();
		
		//double new_R_upstream;
		for (RouteNode child : node.children) {
			if(child.type.equals(RouteNodeType.OPIN)) { //OPIN
				if(con.net.hasOpin()) {
					if(child.index == con.net.getOpin().index) {
						this.addNodeToQueue(node, child, con);
					}
				} else if(!child.used()) {
					this.addNodeToQueue(node, child, con);
				}
			} else if(child.type.equals(RouteNodeType.IPIN)) { //IPIN
				if(child.children[0].target) {
					this.addNodeToQueue(node, child, con);
				}
			} else if(con.isInBoundingBoxLimit(child)) { //ELSE
				this.addNodeToQueue(node, child, con);
			}
		}
	}
	
	private void addNodeToQueue(RouteNode node, RouteNode child, Connection con) {
		float new_partial_path_cost =  node.routeNodeData.getPartialPathCost() + getRouteNodeCost(child, con);
		float new_lower_bound_total_path_cost = getLowerBoundTotalPathCost(child, con, new_partial_path_cost);
		
		this.addNodeToQueue(child, node, new_partial_path_cost, new_lower_bound_total_path_cost);
	}
	private void addNodeToQueue(RouteNode node, RouteNode prev, float new_partial_path_cost, float new_lower_bound_total_path_cost) {
		RouteNodeData nodeData = node.routeNodeData;
		if(!nodeData.pathCostsSet()) this.nodesTouched.add(nodeData);

		nodeData.updatePartialPathCost(new_partial_path_cost);
		if (nodeData.updateLowerBoundTotalPathCost(new_lower_bound_total_path_cost)) { //queue is sorted by lower bound total cost
			node.routeNodeData.prev = prev;
			this.queue.add(node);
		}
	}

	/**
	 * This is just an estimate and not an absolute lower bound.
	 * The routing algorithm is therefore not A* and optimal.
	 * It's directed search and heuristic.
	 */
	private float getLowerBoundTotalPathCost(RouteNode node, Connection con, float partial_path_cost) {
		if(this.alpha == 0) return partial_path_cost;
		RouteNode target = con.sinkRouteNode;
		RouteNodeData data = node.routeNodeData;

		int usage = 1 + data.countSourceUses(con.source);
		
		float expected_cost = this.alpha * ((((float)this.rrg.lowerEstimateConnectionCost(node, target)) / usage) + 0.95f);
		
		float bias_cost = node.baseCost / (2 * con.net.fanout)
							* (float)(Math.abs((0.5 * (node.xlow + node.xhigh)) - con.net.x_geo) + Math.abs((0.5 * (node.ylow + node.yhigh)) - con.net.y_geo))
							/ con.net.hpwl;
		
		return partial_path_cost + expected_cost + bias_cost;
	}

	private float getRouteNodeCost(RouteNode node, Connection con) {
		RouteNodeData data = node.routeNodeData;
		
		boolean containsSource = data.countSourceUses(con.source) != 0;
		
		float pres_cost;
		if (containsSource) {
			int overoccupation = data.numUniqueSources() - node.capacity;
			if (overoccupation < 0) {
				pres_cost = 1;
			} else {
				pres_cost = 1 + overoccupation * this.pres_fac;
			}
		} else {
			pres_cost = data.pres_cost;
		}
		
		return node.baseCost * data.acc_cost * pres_cost / (1 + (10 * data.countSourceUses(con.source)));
	}
	
	private void updateCost(float pres_fac, float acc_fac){
		for (RouteNode node : this.rrg.getRouteNodes()) {
			RouteNodeData data = node.routeNodeData;

			int overuse = data.occupation - node.capacity;
			
			//Present congestion penalty
			if(overuse == 0) {
				data.pres_cost = 1 + pres_fac;
			} else if (overuse > 0) {
				data.pres_cost = 1 + (overuse + 1) * pres_fac;
				data.acc_cost = data.acc_cost + overuse * acc_fac;
			}
		}
	}
}