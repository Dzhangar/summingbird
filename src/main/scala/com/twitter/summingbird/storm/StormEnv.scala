package com.twitter.summingbird.storm

import backtype.storm.{ Config, StormSubmitter }
import com.twitter.scalding.Args
import com.twitter.summingbird.Env
import com.twitter.summingbird.util.KryoRegistrationHelper

/**
 *  @author Oscar Boykin
 *  @author Sam Ritchie
 *
 * Storm-specific extension to Env. StormEnv handles storm-specific configuration
 * and topology submission to the Storm cluster.
 */

case class StormEnv(override val jobName: String, override val args: Args) extends Env(jobName) {
  lazy val config = {
    val flatMappedBuilder = builder.flatMappedBuilder

    val config = new Config
    config.setFallBackOnJavaSerialization(false)
    config.setKryoFactory(classOf[SummingbirdKryoFactory])

    // Register codec pairs for all time and event types.
    KryoRegistrationHelper.registerBijections(config, flatMappedBuilder.eventCodecPairs)

    // Register key and value types. All extensions of either of these
    // types will be caught by the registered codec.
    val codecPairs = Seq(builder.keyCodecPair, builder.valueCodecPair)
    KryoRegistrationHelper.registerBijectionDefaults(config, codecPairs)

    // Reasonable default settings for Summingbird storm
    // jobs. Override these by implementing `transformConfig` in
    // `AbstractJob`.
    config.setMaxSpoutPending(1000)
    config.setNumAckers(12)
    config.setNumWorkers(12)
    config
  }

  def run {
    // Calling abstractJob's constructor and binding it to a variable
    // forces any side effects caused by that constructor (building up
    // of the environment and defining the builder).
    val ajob = abstractJob
    val topo = builder.buildStorm(this)

    val finalConf = {
      val c = ConfigBijection.invert(config)
      ConfigBijection(ajob.transformConfig(c))
    }

    // TODO: We strip everything before the period because of the
    // following issue with storm:
    // https://github.com/nathanmarz/storm/issues/261. Once this is
    // resolved, clean this thing up.

    val classSuffix = jobName.split("\\.").last
    StormSubmitter.submitTopology("summingbird_" + classSuffix, finalConf, topo)
  }
}
