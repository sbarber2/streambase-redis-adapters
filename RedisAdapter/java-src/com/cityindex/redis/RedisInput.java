package com.cityindex.redis;

import org.slf4j.Logger;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import com.streambase.sb.CompleteDataType;
import com.streambase.sb.Schema;
import com.streambase.sb.Schema.Field;
import com.streambase.sb.StreamBaseException;
import com.streambase.sb.TupleException;
import com.streambase.sb.adapter.InputAdapter;
import com.streambase.sb.operator.Parameterizable;
import com.streambase.sb.operator.TypecheckException;

/**
 * Generated by JDT StreamBase Client Templates (Version: 7.3.0.1211091631).
 *
 * Input adapters take information from outside of an application and
 * provide it in the form of a stream or streams to the application.
 * Any implementation of this class <b>must</b> extend class InputAdapter.
 * Input adapters should call sendOutput(int, Tuple) to provide tuples
 * to the application.
 * <p>
 * For in-depth information on implementing a custom adapter, please
 * see "Developing StreamBase Embedded Adapters" in the StreamBase documentation.
 * <p>
 */
public class RedisInput extends InputAdapter implements Parameterizable,
		Runnable {

	public static final long serialVersionUID = 1353942253486L;
	private Logger logger;
	// Properties
	private int redisPort;
	private String channelPath;
	private String redisHost;
	private Schema schema0;
	private Schema schema0runtime;
	private String displayName = "Redis Input Adapter";
    private Jedis subscriberJedis;
    private volatile boolean isShutDownRedis;
	static private JedisPool jedisPool;
    private JedisPoolConfig poolConfig;
    private Field channelField;
    private Field valueField;
    private static final int RESULT_OUTPUT_PORT = 0;

	/**
	 * The constructor is called when the Adapter instance is created, but before the Adapter
	 * is connected to the StreamBase application. We recommended that you set the initial input
	 * port and output port count in the constructor by calling setPortHints(inPortCount, outPortCount).
	 * The default is no input ports or output ports. The constructor may also set default values for
	 * adapter parameters. These values will be displayed in StreamBase Studio when a new adapter is
	 * dragged to the canvas, and serve as the default values for omitted optional parameters.
	 */
	public RedisInput() {
		super();
		logger = getLogger();
		setPortHints(0, 1);
		setDisplayName(displayName);
		setShortDisplayName(this.getClass().getSimpleName());
		setChannelPath("SampleKey");
		setRedisPort(6379);
		setRedisHost("127.0.0.1");

	}

	/**
	 * Typecheck this adapter. The typecheck method is called after the adapter instance is
	 * constructed, and once input ports are connected. The adapter should validate its parameters
	 * and throw PropertyTypecheckException (or TypecheckException) if any problems are found.
	 * The message associated with the thrown exceptions will be displayed in StreamBase Studio during authoring,
	 * or printed on the console by sbd. Input adapters should set the schema of each output
	 * port by calling the setOutputSchema(portNum, schema) method for each output port.
	 * If the adapter needs to change the number of input ports based on parameter values,
	 * it should call requireInputPortCount(portCount) at this point.
	 */
	public void typecheck() throws TypecheckException {
		// Sets the output schema for the output port

		if (schema0 == null){
			schema0 = new Schema(
					"RedisMessage",
					new Field("channel", CompleteDataType.forString()),
					new Field("value", CompleteDataType.forString()) );
		}
		setOutputSchema(RESULT_OUTPUT_PORT, schema0);
	}

	/**
	 * Initialize the adapter. If typecheck succeeds, the init method is called before
	 * the StreamBase application is started. Note that your adapter is not required to
	 * define the init method, unless you need to register a runnable or perform
	 * initialization of a resource such as, for example, a JDBC pool.
	 */
	public void init() throws StreamBaseException {
		super.init();
		isShutDownRedis = false;
        poolConfig = new JedisPoolConfig();
		logger.debug("poolConfig created");
        jedisPool = new JedisPool(poolConfig, redisHost, redisPort, 0);
		logger.debug("pool created");
		subscriberJedis = jedisPool.getResource();
		logger.debug("subscriberJedis created");
		
		// Register the object so it will be run as a thread managed by StreamBase. 	
		registerRunnable(this, true);
		
	}

	/**
	 * Shutdown adapter by cleaning up any resources used (e.g. close an output file
	 * if it has been opened). When the application is shutting down, the adapter's shutdown.
	 * method will be called first. Once this has returned, all threads should exit and the
	 * adapter is considered shutdown.
	 */
	public void shutdown() {
		logger.debug(this.getName()+": Entering shutdown()");
		isShutDownRedis = true;
		//send a message so the blocked subscription thread will shut down
		try (Jedis j = jedisPool.getResource()) {
			j.connect();
			j.publish("_UnBlock",  "True");
			j.disconnect();
		}
		logger.debug(this.getName()+": Exiting shutdown()");
	}

	/**
	 * Main thread of the adapter. At this point, the application begins to run.
	 * StreamBase will start threads for any managed runnables registered by
	 * earlier calls to registerRunnable. Input adapters can call sendOutput(port, tuple)
	 * at any time to output a tuple to the specified port.
	 * 
	 * 
	 * Note the subscription to the get channel path, i.e. all publications
	 * 
	 */
	public void run() {
		shouldRun();

		schema0runtime = getRuntimeOutputSchema(0);
		try {
			channelField = schema0runtime.getField("channel");
			valueField = schema0runtime.getField("value");
		} catch (TupleException e1) {
			logger.error("Exception creating runtime tuple fields", e1);
		}

		subscriberJedis.psubscribe(new JedisPubSub() {
            public void onMessage(String channel, String message) {
            	logger.debug(getName()+ ": onMessage: channel " + channel + ", messsage " + message);
    			if (shouldRun()) {
    				try {
	        	        com.streambase.sb.Tuple result = schema0runtime.createTuple();	
	        	        result.setString(channelField, channel);
	                    result.setString(valueField, message);
	            		if (isShutDownRedis) { 
	            			logger.debug(getName()+ "isShutDownRedis="+isShutDownRedis);
	            			unsubscribe(); }
	            		else {sendOutput(RESULT_OUTPUT_PORT, result);}
        			

    				} catch (StreamBaseException e) {
	        			logger.error("Exception sending output to port", e);
	        			return;
	        		}
    			}
            }

            public void onSubscribe(String channel, int subscribedChannels) {
            	logger.debug("Subscribed " + channel);
            }

            public void onUnsubscribe(String channel, int subscribedChannels) {
            	logger.debug("unsubscribed " + channel);
            }

            public void onPSubscribe(String pattern, int subscribedChannels) {
            	logger.debug("pSubscribed " + pattern);
            }

            public void onPUnsubscribe(String pattern, int subscribedChannels) {
            	logger.debug("pUnSubscribed " + pattern);
            }

            public void onPMessage(String pattern, String channel,
                    String message) {
            	logger.debug(getName()+ ": onPMessage: pattern " + pattern+ ", channel " + channel + ", messsage " + message);
    			if (shouldRun()) {
    				try {
	        	        com.streambase.sb.Tuple result = schema0runtime.createTuple();	
	        	        result.setString(channelField, channel);
	                    result.setString(valueField, message);
	            		if (isShutDownRedis) { 
	            			logger.debug(getName()+ "isShutDownRedis="+isShutDownRedis);
	            			punsubscribe(); 
	            			}
	            		else {sendOutput(RESULT_OUTPUT_PORT, result);}       			

    				} catch (StreamBaseException e) {
	        			logger.error("Exception sending output to port", e);
	        			return;
	        		}
    			}
            }

        }, getChannelPath());

		subscriberJedis.close();
		jedisPool.destroy();
		logger.debug("left run method for Redis Input " );
	}



	/***************************************************************************************
	 * The getter and setter methods provided by the Parameterizable object.               *
	 * StreamBase Studio uses them to determine the name and type of each property         *
	 * and obviously, to set and get the property values.                                  *
	 ***************************************************************************************/

	public void setRedisPort(int redisPort) {
		this.redisPort = redisPort;
	}

	public int getRedisPort() {
		return this.redisPort;
	}
	public void setChannelPath(String channelPath) {
		this.channelPath = channelPath;
	}

	public String getChannelPath() {
		String cleanChannelPath = this.channelPath.trim();
		if (cleanChannelPath == "") {cleanChannelPath = "*";}
		return cleanChannelPath;
	}


	public void setRedisHost(String redisHost) {
		this.redisHost = redisHost;
	}

	public String getRedisHost() {
		return this.redisHost;
	}

	/** For detailed information about shouldEnable methods, see interface Parameterizable java doc
	 *  @see Parameterizable
	 */
	public boolean shouldEnableChannelPath() {
		return true;
	}


	public boolean shouldEnableRedisPort() {
		return true;
	}

	public boolean shouldEnableRedisHost() {
		return true;
	}

}
