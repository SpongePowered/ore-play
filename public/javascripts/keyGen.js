var pluginId = null;
var keyGenText = null;
var keyRevokeText = null;

var KEY_TYPE_DEPLOYMENT = 0;

function bindKeyGen(e) {
    e.click(function() {
        var spinner = $(this).find('.fa-spinner').show();
        var $this = $(this);
        $.ajax({
            url: '/api/projects/' + pluginId + '/keys/new',
            method: 'post',
            data: {csrfToken: csrf, 'key-type': KEY_TYPE_DEPLOYMENT},
            dataType: 'json',
            success: function(key) {
                console.log(key);
                $('.input-key').val(key.value);
                $this.removeClass('btn-key-gen btn-info')
                    .addClass('btn-key-revoke btn-danger')
                    .data('key-id', key.id)
                    .text(keyRevokeText)
                    .off('click');
                bindKeyRevoke($this);
            },
            complete: function() {
                spinner.hide();
            }
        })
    });
}

function bindKeyRevoke(e) {
    e.click(function() {
        var spinner = $(this).find('.spinner').show();
        var $this = $(this);
        $.ajax({
            url: '/api/projects/' + pluginId + '/keys/revoke',
            method: 'post',
            data: {csrfToken: csrf, 'id': $(this).data('key-id')},
            success: function() {
                $('.input-key').val('');
                $this.removeClass('btn-key-revoke btn-danger')
                    .addClass('btn-key-gen btn-info')
                    .text(keyGenText)
                    .off('click');
                bindKeyGen($this);
            },
            complete: function() {
                spinner.hide();
            }
        })
    });
}

$(function() {
    bindKeyGen($('.btn-key-gen'));
    bindKeyRevoke($('.btn-key-revoke'));
});
