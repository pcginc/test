package com.ociweb.iot.metronome;

import com.ociweb.iot.grove.Grove_LCD_RGB;
import com.ociweb.iot.maker.AnalogListener;
import com.ociweb.iot.maker.CommandChannel;
import com.ociweb.iot.maker.DigitalListener;
import com.ociweb.iot.maker.DeviceRuntime;
import com.ociweb.iot.maker.PayloadReader;
import com.ociweb.iot.maker.Port;
import com.ociweb.iot.maker.PubSubListener;
import com.ociweb.iot.maker.StartupListener;
import com.ociweb.iot.maker.TimeListener;
import static com.ociweb.iot.grove.GroveTwig.AngleSensor;

/*
 * Beats per minute   (build an ENUM of these so we can display the names on the screen.
 * 
 * Largo 40-60
 * Larghetto 60-66
 * Adagio 66-76
 * Andante 76-108
 * Moderato 108-120
 * Allegro 120-168
 * Presto 168-200
 * Prestissimo 200-208
 * 
 * 
 * 1 minute = 60_000 ms
 * 40  BPM = 1500ms
 * 300 BPM =  200ms  required (max err +-2ms)
 * 600 BPM =  100ms  nice   (max err +-1ms)
 * 
 * Test at 40, 60, 120 and 208,  the error must be < 1% 
 *   
 * 
 */

public class MetronomeBehavior implements AnalogListener, PubSubListener, StartupListener, TimeListener {

    private final CommandChannel tickCommandChannel;
    private final CommandChannel screenCommandChannel;
    
    private final String topic = "tick";
              
    private static final int BBM_SLOWEST     = 40;
    private static final int BBM_FASTEST     = 208;
    
    private static final int BBM_VALUES      = 1+BBM_FASTEST-BBM_SLOWEST;    
    
    private int  requestedPBM;
    private long requestDuration;
    
    private long base;
    private int beatIdx; 
    private int activeBPM;
    
    private int showingBPM;
    
    public MetronomeBehavior(DeviceRuntime runtime) {
        this.tickCommandChannel = runtime.newCommandChannel();
        this.screenCommandChannel = runtime.newCommandChannel();
    }

    @Override
    public void startup() {
        tickCommandChannel.subscribe(topic,this); 
        tickCommandChannel.openTopic(topic).publish();
        
        Grove_LCD_RGB.commandForColor(tickCommandChannel, 255, 255, 255);
        
    }

    @Override
    public void analogEvent(Port port, long time, long durationMillis, int average, int value) { 
    	requestedPBM=  BBM_SLOWEST + ((BBM_VALUES*average)/AngleSensor.range());   
        requestDuration = durationMillis;    
    }    

    @Override
    public void message(CharSequence topic, PayloadReader payload) {
        
    	
        if (requestedPBM>0) {

            if (activeBPM != requestedPBM && (requestDuration > 100 || activeBPM==0) ) {
            	
            	activeBPM = requestedPBM;
                base = System.currentTimeMillis();
                beatIdx = 0;
            } 
                                    
            long delta = (++beatIdx*60_000)/activeBPM;
            long until = base + delta;
            tickCommandChannel.digitalPulse(IoTApp.BUZZER_PORT,500_000);     
            tickCommandChannel.blockUntil(IoTApp.BUZZER_PORT, until); 
            

            if (beatIdx==activeBPM) {
            	beatIdx = 0;
            	base += 60_000; 
            }
            
        }
        tickCommandChannel.openTopic(topic).publish();//request next tick
        
    }


    @Override
    public void timeEvent(long time) {
       if (requestedPBM != showingBPM) {
                      
           String tempo;
           if (requestedPBM<108){
        	   if(requestedPBM<66){
        	       tempo = requestedPBM<60 ? "Largo" : "Larghetto";
        	   } else{
        	       tempo = requestedPBM<76 ? "Adagio" : "Andante";
        	   }
           }else{
        	   if(requestedPBM<168){
        	       tempo = requestedPBM<120 ? "Moderato" : "Allegro";
        	   } else {
        	       tempo = requestedPBM<200 ? "Presto" : "Prestissimo";
        	   }
           }
           
           String bpm = Integer.toString(requestedPBM);
           if (requestedPBM<100) {
               bpm = "0"+bpm;
           }
           
           if (Grove_LCD_RGB.commandForText(screenCommandChannel, bpm+"-"+tempo)) {
               showingBPM = requestedPBM;
           }
           
       }
        
    }



}
