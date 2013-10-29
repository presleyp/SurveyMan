%1$s

// hide Previous buttons because in an experiment you shouldn't
// be able to double check your previous responses
function hidePrev(){
    $('input[value="Prev"]').hide();
}


$(document).ready(function(){
    hidePrev();
});
