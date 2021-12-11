package dev.technici4n.moderndynamics.network.item;

import com.google.common.collect.Lists;
import dev.technici4n.moderndynamics.attachment.InhibitorAttachmentItem;
import dev.technici4n.moderndynamics.network.NetworkNode;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class ItemPathCache {
	private final Map<SidedNode, List<ItemPath>> cache = new HashMap<>();

	public List<ItemPath> getPaths(NetworkNode<ItemHost, ItemCache> startingPoint, Direction startingSide) {
		return cache.computeIfAbsent(new SidedNode(startingPoint, startingSide), ItemPathCache::computePaths);
	}

	private static List<ItemPath> computePaths(SidedNode startingPoint) {
		// First, gather all targets, sorted by priority.
		PriorityQueue<PqNode> pq = new PriorityQueue<>(Comparator.comparingLong(PqNode::distance));
		Reference2LongMap<NetworkNode<ItemHost, ItemCache>> distance = new Reference2LongOpenHashMap<>();
		Map<NetworkNode<ItemHost, ItemCache>, Direction> prevDirection = new IdentityHashMap<>();
		Map<NetworkNode<ItemHost, ItemCache>, NetworkNode<ItemHost, ItemCache>> prevNode = new IdentityHashMap<>();
		List<SidedNode> targets = new ArrayList<>();

		pq.add(new PqNode(startingPoint.node, 0));
		distance.put(startingPoint.node, 0);

		while (!pq.isEmpty()) {
			var currentPqNode = pq.poll();
			var currentNode = currentPqNode.node;
			long currentDistance = currentPqNode.distance;

			if (currentDistance != distance.getLong(currentNode)) {
				continue;
			}

			for (var side : currentNode.getHost().getInventoryConnections()) {
				targets.add(new SidedNode(currentNode, side));
			}

			for (var connection : currentNode.getConnections()) {
				long edgeWeight = 1;
				if (currentNode.getHost().getAttachment(connection.direction).getItem() instanceof InhibitorAttachmentItem) {
					edgeWeight = 1000;
				}
				long newDistance = currentDistance + edgeWeight;
				if (distance.getOrDefault(connection.target, Long.MAX_VALUE) > newDistance) {
					distance.put(connection.target, newDistance);
					pq.add(new PqNode(connection.target, newDistance));
					prevDirection.put(connection.target, connection.direction);
					prevNode.put(connection.target, currentNode);
				}
			}
		}

		// Build the paths
		List<ItemPath> computedPaths = new ArrayList<>(targets.size());
		for (var target : targets) {
			for (var side : target.node.getHost().getInventoryConnections()) {
				if (target.node == startingPoint.node && side == startingPoint.side.getOpposite()) {
					continue; // prevent insertion back into the source
				}
				var adjPos = target.node.getHost().getPipe().getPos().offset(side);

				// Backtrack to find the path.
				List<Direction> reversedPath = new ArrayList<>();
				var current = target.node;
				var currentDir = side;
				while (current != null) {
					reversedPath.add(currentDir);
					currentDir = prevDirection.get(current);
					current = prevNode.get(current);
				}
				reversedPath.add(startingPoint.side);
				Direction[] path = Lists.reverse(reversedPath).toArray(Direction[]::new);
				computedPaths.add(new ItemPath(startingPoint.node.getHost().getPipe().getPos(), adjPos, path));
			}
		}

		return computedPaths;
	}

	private record SidedNode(NetworkNode<ItemHost, ItemCache> node, Direction side) {}
	private record PqNode(NetworkNode<ItemHost, ItemCache> node, long distance) {}
}
