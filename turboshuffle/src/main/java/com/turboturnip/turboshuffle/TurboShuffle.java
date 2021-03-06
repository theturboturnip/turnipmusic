package com.turboturnip.turboshuffle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@SuppressWarnings("WeakerAccess")
public class TurboShuffle {

	public class State {
		List<SongPoolKey> songHistory;
		List<Integer> poolHistory;

		Map<SongPoolKey, Integer> songOccurrences; // number of occurences for each song
		int[] poolOccurrences; // number of occurences for each pool

		public State(){
			songHistory = new ArrayList<>();
			poolHistory = new ArrayList<>();
			songOccurrences = new HashMap<>();
			poolOccurrences = new int[TurboShuffle.this.songPools.length];
		}

		public void IncrementHistory(SongPoolKey song){
			switch(TurboShuffle.this.config.probabilityMode) {
				case BySong:
					songHistory.add(song);
					songOccurrences.put(song, getSongOccurrences(song) + 1);
					if (poolHistory.size() == 0 || (poolHistory.get(poolHistory.size() - 1) != song.poolIndex))
						poolHistory.add(song.poolIndex);
					poolOccurrences[song.poolIndex]++;
					break;
				case ByLength:
					int minutes = Common.secondsToMinutes(TurboShuffle.this.GetSongFromKey(song).getLengthInSeconds());
					songOccurrences.put(song, getSongOccurrences(song) + minutes);
					poolOccurrences[song.poolIndex] += minutes;
					boolean newPool = poolHistory.size() == 0 || (poolHistory.get(poolHistory.size()) != song.poolIndex);
					while (minutes > 0) {
						songHistory.add(song);
						if (newPool)
							poolHistory.add(song.poolIndex);
						minutes--;
					}
					break;
			}

			while (songHistory.size() > TurboShuffle.this.config.maximumMemory){
				SongPoolKey songToRemove = songHistory.remove(0);
				// If this song was the last song for this pool in the history, also remove the pool
				if (songHistory.get(0).poolIndex != poolHistory.get(0))
					poolHistory.remove(0);
				// Decrement the occurrences for the song and the pool
				songOccurrences.put(songToRemove, getSongOccurrences(song, 1) - 1);
				poolOccurrences[songToRemove.poolIndex]--;
			}
		}

		int getSongOccurrences(SongPoolKey song){
			return getSongOccurrences(song, 0);
		}
		int getSongOccurrences(SongPoolKey song, int defaultVal){
			if (!songOccurrences.containsKey(song)) return 0;
			return songOccurrences.get(song);
		}
	}

	public final TurboShuffleConfig config;
	public final SongPool[] songPools;

	public TurboShuffle(TurboShuffleConfig config, SongPool... songPools){
		if (songPools.length == 0)
			throw new IllegalArgumentException(
					"Tried to create a shuffle without any pools!"
			);

		if (songPools.length != config.poolWeights.length)
			throw new IllegalArgumentException(
					"Tried to create a shuffle where the amount of pool weights (" + config.poolWeights.length + ") " +
							" does not equal the amount of pools (" + songPools.length + ")"
			);
		this.config = config;

		// Make sure song pools don't have common songs.
		// For 4 song pools 1, 2, 3, 4
		// Test 1-2, 1-3, 1-4, 2-3, 2-4, 3-4
			// For N song pools 1, 2.. n
			// Test 1-2..n, 2-3..n, 3-4..n etc.
		for(int pool1 = 0; pool1 < songPools.length - 1; pool1++) {
			for (int pool2 = pool1 + 1; pool2 < songPools.length; pool2++) {
				// Test the pools at pool1 and pool2 against each other
				for (TurboShuffleSong song : songPools[pool1].songs) {
					if (songPools[pool2].songs.contains(song))
						throw new IllegalArgumentException(
								"Tried to create a shuffle where song pools " + pool1 + " and " + pool2 +
										" both contained the song ID " + song.getId().toString()
						);
				}
			}
		}
		this.songPools = songPools;
	}

	public TurboShuffleSong GetSongFromKey(SongPoolKey key){
		return songPools[key.poolIndex].songs.get(key.songIndexInPool);
	}

	private int randomIndexUsingWeights(Random rng, float[] weights){
		float total = 0;
		for (float w : weights) total += w;

		float value = rng.nextFloat() * total;
		int i;
		for (i = 0; i < weights.length; i++){
			value -= weights[i];
			if (value <= 0) break;
		}
		return i;
	}

	private float historyDistribution(float currentWeight, float totalChiSquared){
		return (float)(currentWeight * Math.pow(totalChiSquared + 1, -config.historySeverity));
	}
	private float clumpProbability(int currentClumpLength, float targetClumpLength){
		// Approximate the Heaviside Step function
		// https://en.wikipedia.org/wiki/Heaviside_step_function
		// 1/(1+e^2kx) where x = delta to streak, k = clump power
		float exponent = -2 * config.clumpSeverity * (targetClumpLength - currentClumpLength);
		if (exponent < -50)
			return 0.5f + config.clumpWeight * 0.5f;
		else if (exponent > 50)
			return 0.5f - config.clumpWeight * 0.5f;
		return (float)(1.0f/(1 + Math.exp(exponent)) - 0.5f) * config.clumpWeight + 0.5f;
	}
	private float clumpDistribution(float currentWeight, float clumpProbability){
		return currentWeight * (clumpProbability + (1 - 2 * clumpProbability) * config.clumpType);
	}
	private float clumpLengthDistribution(float currentWeight, float currentDeltaClumpLength, float selectedSongLength){
		if (config.clumpType > 0.5f) return currentWeight;
		return (float)(currentWeight * Math.pow(selectedSongLength / currentDeltaClumpLength + 1, -config.clumpLengthSeverity * (1 - 2 * config.clumpType)));
	}

	public SongPoolKey NextKey(State shuffleState, Random rng){
		int nextPoolIndex = -1;
		int nextSongIndexInPool = -1;

		int historyEndIndex = -1, currentPoolIndex = -1, currentClumpLength = -1;
		if (shuffleState.songHistory.size() > 0) {
			// Clump variables
			historyEndIndex = shuffleState.poolHistory.size() - 1;
			currentPoolIndex = shuffleState.poolHistory.get(historyEndIndex);
			currentClumpLength = 0;
			for (int i = historyEndIndex; i >= 0; i--){
				if (currentPoolIndex != shuffleState.poolHistory.get(i))
					continue;
				currentClumpLength++;
			}
		}

		boolean allowPrint = false;
		//allowPrint = (shuffleState.songHistory.size() < 5);

		// Step 1: Determine the weights for each pool based on history, clumping and the desired ratios
		float poolWeights[] = new float[songPools.length];
		System.arraycopy(config.poolWeights, 0, poolWeights, 0, songPools.length);
		//if (shuffleState.songHistory.size() == 0)
		//	System.out.println(config.poolWeights[0] + " = " + poolWeights[0]);
		{
			// History part
			for (int i = 0; i < songPools.length; i++) {
				// Find the values of the pool ratios (i.e. A:B 3:1) if we played a song from pool i next.
				// Use the chi-squared values to determine the "difference" of this ratio from the desired ratio.
				// Bias the pool chances with these values.

				float totalChiSquared = 0;
				for (int j = 0; j < songPools.length; j++) {
					float expected = config.sumToOnePoolWeights[j] * (shuffleState.songHistory.size() + 1);
					float actual = shuffleState.poolOccurrences[j] + (j == i ? (config.probabilityMode == TurboShuffleConfig.ProbabilityMode.BySong ? 1 : songPools[j].averageLengthInMinutes) : 0);
					float chiSquared = (float) Math.pow(1 - actual / expected, 2);
					totalChiSquared += chiSquared;
				}
				//if (allowPrint)
				//	System.out.println(totalChiSquared);
				poolWeights[i] = historyDistribution(poolWeights[i], totalChiSquared);
			}
			// Clump part
			if (shuffleState.songHistory.size() > 0){
				// Split the pools into two groups:
					// The pool we're currently using gets the "continue" modifier
					// All other pools get the "change" modifier, which is 1 - the "continue" modifier
				// The continue modifier is a smoothed step function

				float continueWithCurrentPoolProbability = clumpProbability(currentClumpLength, config.poolWeights[currentPoolIndex]);
				float changePoolProbability = 1 - continueWithCurrentPoolProbability;

				if (allowPrint)
					System.out.println(continueWithCurrentPoolProbability);

				poolWeights[currentPoolIndex] = clumpDistribution(poolWeights[currentPoolIndex], continueWithCurrentPoolProbability);
				for (int i = 0; i < songPools.length; i++){
					if (i == currentPoolIndex) continue;

					poolWeights[i] = clumpDistribution(poolWeights[i], changePoolProbability);
				}
			}
		}

		// Step 2: Select a pool to take the song from using these weights


		nextPoolIndex = randomIndexUsingWeights(rng, poolWeights);
		/*if (allowPrint){
			System.out.print("[ ");
			for(float w : poolWeights)
				System.out.print(w + " ");
			System.out.println("]");
			System.out.println(nextPoolIndex);
		}*/

		// Step 3: Determine the weights for each song in the pool based on history, clumping+length (NEW)
		float songWeights[] = new float[songPools[nextPoolIndex].songs.size()];
		java.util.Arrays.fill(songWeights, 1.0f);

		float targetClumpLength = config.poolWeights[nextPoolIndex];
		for (int i = 0; i < songWeights.length; i++) {
			int currentSongLengthInMinutes = Common.secondsToMinutes(songPools[nextPoolIndex].songs.get(i).getLengthInSeconds());

			// History part
			{
				// Find the values of the song ratios (i.e. A:B 3:1) if we played song i next.
				// Use the chi-squared values to determine the "difference" of this ratio from the desired 1:1:1...:1 ratio.
				// Bias the song chances with these values.

				float totalChiSquared = 0;
				for (int j = 0; j < songWeights.length; j++) {
					float expected = (shuffleState.poolOccurrences[nextPoolIndex] + 1) / (float)(songWeights.length);
					float actual = shuffleState.getSongOccurrences(new SongPoolKey(nextPoolIndex, j))
							+ (j == i ? (config.probabilityMode == TurboShuffleConfig.ProbabilityMode.BySong ? 1 : currentSongLengthInMinutes) : 0);
					float chiSquared = (float) Math.pow(1 - actual / expected, 2);
					totalChiSquared += chiSquared;
				}

				songWeights[i] = historyDistribution(songWeights[i], totalChiSquared);
			}
			// Clumping part
			// If we're clumping, try to select a song that fits within the current clump
			if (config.clumpType < 0.5f && shuffleState.songHistory.size() > 0 && config.probabilityMode == TurboShuffleConfig.ProbabilityMode.ByLength){
				songWeights[i] = clumpLengthDistribution(songWeights[i], targetClumpLength - currentClumpLength, currentSongLengthInMinutes);
			}
		}

		// Step 4: Select a song using these weights
		nextSongIndexInPool = randomIndexUsingWeights(rng, songWeights);

		return new SongPoolKey(nextPoolIndex, nextSongIndexInPool);
	}
	public TurboShuffleSong NextSong(State shuffleState, Random rng){
		return GetSongFromKey(NextKey(shuffleState, rng));
	}
}
