/*
 * ==================================================
 *  _____             _
 * |     |___ ___    |_|___
 * |  |  |  _| -_|_  | |_ -|
 * |_____|_| |___|_|_| |___|
 *                 |___|
 *
 * By Walker Crouse (windy) and contributors
 * (C) SpongePowered 2016-2017 MIT License
 * https://github.com/SpongePowered/Ore
 *
 * Powers the changes that need to be made when the site
 * is viewed offline
 *
 * ==================================================
 */

function showOfflineAlert(){
    var alert = $('#alert-warning-offline');
    alert.fadeIn();
}

function hideOfflineAlert(){
    var alert = $('#alert-warning-offline');
    alert.fadeOut();
}

$(function() {
    if(typeof IS_OFFLINE_PAGE === 'undefined'){
        // place the alert to the right position
        $('#alert-warning-offline').prependTo('.container:eq(1)');

        // event listener to change the visibility of the offline alert
        window.addEventListener("offline", function() { showOfflineAlert(); });
        window.addEventListener("online", function() { hideOfflineAlert(); });

        // show the alert if the page is already loaded offline
        if(!navigator.onLine){
            showOfflineAlert();
        }
    }
});
