%1$s
/* This file is for individual customization. Define any functions
 * you'd like, and call them in customInit, which is called by the
 * main Javascript file when the document is loaded.
 */

// hide Previous buttons because in an experiment you shouldn't
// be able to double check your previous responses
function hidePrev(){
    $('input[value="Prev"]').hide();
}

// called when a survey is loaded
function customInit(){
   hidePrev();
}
