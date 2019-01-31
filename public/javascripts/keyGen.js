//=====> Nonce Variables

var PLUGIN_ID = null;
var KEY_GEN_TEXT = null;
var KEY_REVOKE_TEXT = null;
var KEY_TYPE_DEPLOYMENT = 0;


//=====> jQuery Doc Ready

$(function() {
    var keyGenBtn = $('.btn-key-gen');

    keyGenBtn.on('click', function () {
        keyGenBtn.find('.spinner').toggle();

        var route = jsRoutes.controllers.ApiController.createKey(API_VERSION, PLUGIN_ID);
        $.ajax({
            url: route.url,
            type: route.type,
            data: { 'key-type': KEY_TYPE_DEPLOYMENT }
        }).always(function () {
            keyGenBtn.find('.spinner').toggle();
        }).done(function (key) {
            $('.input-key').val(key.value);

            keyGenBtn.find('.text').text(KEY_REVOKE_TEXT);
            keyGenBtn.removeClass('btn-key-gen btn-info')
                .addClass('btn-key-revoke btn-danger')
                .data('key-id', key.id)
                .off('click');
        });
    });

    bindKeyRevoke($('.btn-key-revoke'));
});


function bindKeyRevoke(e) {
    e.click(function() {
        var spinner = $(this).find('.spinner').toggle();
        var $this = $(this);
        $.ajax({
            url: '/api/projects/' + PLUGIN_ID + '/keys/revoke',
            method: 'post',
            data: {csrfToken: csrf, 'id': $(this).data('key-id')},
            success: function() {
                $('.input-key').val('');
                $this.removeClass('btn-key-revoke btn-danger')
                    .addClass('btn-key-gen btn-info')
                    .off('click');
                $this.find('.text').text(KEY_GEN_TEXT);
            },
            complete: function() {
                e.find('.spinner').toggle();
            }
        })
    });
}
