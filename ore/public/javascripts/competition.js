var competitionId;

function initEditBanner() {
    $('.banner-some').mouseenter(function() {
        $('.banner-edit').show();
    }).mouseleave(function() {
        $('.banner-edit').hide();
    });
}

$(function() {
    var input = $('input[name="banner"]');
    $('.banner-upload').click(function(e) {
        e.preventDefault();
        input.click();
    });

    initEditBanner();

    input.change(function() {
        var bannerImg = $('.banner-image');
        if (bannerImg.length > 0)
            bannerImg.remove();
        var formData = new FormData();
        formData.append('banner', input[0].files[0]);
        $.ajax({
            xhr: function() {
                var progress = $('.banner').find('.progress').css('visibility', 'visible');
                var xhr = new window.XMLHttpRequest();
                xhr.upload.addEventListener("progress", function(evt) {
                    if (evt.lengthComputable) {
                        var pcent = Math.round(evt.loaded / evt.total * 100);
                        progress.find('.progress-bar').css('width', pcent + '%').attr('aria-valuenow', pcent);
                    }
                }, false);
                return xhr;
            },
            type: 'post',
            url: '/competitions/' + competitionId + '/banner',
            data: formData,
            dataType: 'json',
            processData: false,
            contentType: false,
            success: function(json) {
                if (json.hasOwnProperty('error'))
                    ; // TODO
                else {
                    var banner = $('.banner');
                    banner
                        .append('<img class="banner-image" src="' + json['bannerUrl'] + '" />')
                        .addClass('banner-some');
                    banner.find('.banner-none').hide();
                    initEditBanner();
                }
            }
        });
    });

});
